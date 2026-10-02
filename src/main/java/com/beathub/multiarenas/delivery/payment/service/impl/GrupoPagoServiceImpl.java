package com.beathub.multiarenas.delivery.payment.service.impl;

import com.beathub.multiarenas.delivery.payment.client.OrderingServiceClient;
import com.beathub.multiarenas.delivery.payment.client.dto.PedidoClientResponse;
import com.beathub.multiarenas.delivery.payment.dto.request.*;
import com.beathub.multiarenas.delivery.payment.dto.response.*;
import com.beathub.multiarenas.delivery.payment.entity.GrupoPago;
import com.beathub.multiarenas.delivery.payment.entity.GrupoPagoUsuario;
import com.beathub.multiarenas.delivery.payment.entity.PedidoPago;
import com.beathub.multiarenas.delivery.payment.entity.TipoGrupoPago;
import com.beathub.multiarenas.delivery.payment.exception.BadRequestException;
import com.beathub.multiarenas.delivery.payment.exception.ForbiddenException;
import com.beathub.multiarenas.delivery.payment.exception.ResourceNotFoundException;
import com.beathub.multiarenas.delivery.payment.repository.GrupoPagoRepository;
import com.beathub.multiarenas.delivery.payment.repository.GrupoPagoUsuarioRepository;
import com.beathub.multiarenas.delivery.payment.repository.PedidoPagoRepository;
import com.beathub.multiarenas.delivery.payment.repository.TipoGrupoPagoRepository;
import com.beathub.multiarenas.delivery.payment.service.GrupoPagoService;
import com.beathub.multiarenas.delivery.payment.service.PaymentService;
import com.beathub.multiarenas.delivery.payment.service.QrCodeGeneratorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class GrupoPagoServiceImpl implements GrupoPagoService {

    private final GrupoPagoRepository grupoPagoRepository;
    private final GrupoPagoUsuarioRepository grupoPagoUsuarioRepository;
    private final TipoGrupoPagoRepository tipoGrupoPagoRepository;
    private final PedidoPagoRepository pedidoPagoRepository;
    private final OrderingServiceClient orderingClient;
    private final PaymentService paymentService;
    private final QrCodeGeneratorService qrCodeGeneratorService;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private jakarta.servlet.http.HttpServletRequest httpRequest;

    @Value("${app.pwa.base-url:https://delivery-daviarena-stg.beat-hub.com}")
    private String pwaBaseUrl;

    @Override
    @Transactional
    public GrupoPagoResponse crearGrupo(CrearGrupoPagoRequest request, String arenaId, Long usuarioId, String authToken) {
        if (request.getPedidoId() == null) {
            throw new BadRequestException("El pedidoId es obligatorio para crear un grupo de pago");
        }

        // Verificar si ya existe un grupo de pago activo para este pedido
        grupoPagoRepository.findByPedidoIdAndEstadoId(request.getPedidoId(), 1).ifPresent(existing -> {
            throw new BadRequestException("Ya existe un grupo de pago activo para el pedido #" + request.getPedidoId()
                    + " con código [" + existing.getCodigoUnico() + "]");
        });

        // Consultar el pedido en el servicio de Ordering para validar monto y pertenencia
        BigDecimal valorTotal = request.getValorTotal() != null ? request.getValorTotal() : BigDecimal.ZERO;
        String arenaIdFinal = arenaId;

        Optional<PedidoClientResponse> pedidoOpt = orderingClient.getPedido(request.getPedidoId(), authToken);
        if (pedidoOpt.isPresent()) {
            PedidoClientResponse pedido = pedidoOpt.get();
            // Estados 13 (Pagado) y 18 (Entregado) ya no admiten abrir una vaca de pago
            if (pedido.getEstadoId() != null && (pedido.getEstadoId().equals(13) || pedido.getEstadoId().equals(18))) {
                throw new BadRequestException("El pedido #" + request.getPedidoId() + " ya se encuentra pagado o entregado (Estado: " + pedido.getEstadoId() + ")");
            }
            if (pedido.getValorTotal() != null && pedido.getValorTotal().compareTo(BigDecimal.ZERO) > 0) {
                valorTotal = pedido.getValorTotal();
            }
            if (arenaIdFinal == null || arenaIdFinal.isBlank()) {
                arenaIdFinal = pedido.getArenaId();
            }
        } else {
            // Si el pedido no fue encontrado en Ordering y tampoco enviaron valorTotal válido en el request
            if (valorTotal.compareTo(BigDecimal.ZERO) <= 0) {
                throw new ResourceNotFoundException("No se encontró el pedido #" + request.getPedidoId()
                        + " en el sistema de órdenes. Verifique que el ID del pedido sea correcto.");
            }
            log.warn("No se pudo verificar el pedido #{} en Ordering Service. Procediendo con el valorTotal proporcionado en la solicitud: {}",
                    request.getPedidoId(), valorTotal);
        }

        if (valorTotal.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BadRequestException("El valor total del pedido a dividir debe ser mayor a 0.");
        }

        LocalDateTime fechaExpiracion = request.getFechaExpiracion() != null
                ? request.getFechaExpiracion()
                : LocalDateTime.now().plusHours(4);

        // Modalidad de división: POR_PARTES_IGUALES vs LIBRE_PAGO
        String modalidad = normalizarModalidadDivision(request.getDivisionTipo());
        boolean esLibre = "LIBRE_PAGO".equals(modalidad);

        // Determinar cantidad de personas y cuota preasignada
        int numPersonas = 2; // Por defecto al menos 2 personas para dividir
        if (request.getCantidadPersonas() != null && request.getCantidadPersonas() > 1) {
            numPersonas = request.getCantidadPersonas();
        } else if (request.getParticipantes() != null && !request.getParticipantes().isEmpty()) {
            numPersonas = Math.max(numPersonas, 1 + request.getParticipantes().size());
        }

        BigDecimal cuotaPreasignada = null;
        BigDecimal porcentajePreasignado = null;
        if (!esLibre) {
            cuotaPreasignada = valorTotal.divide(BigDecimal.valueOf(numPersonas), 2, RoundingMode.HALF_UP);
            porcentajePreasignado = BigDecimal.valueOf(100.0).divide(BigDecimal.valueOf(numPersonas), 2, RoundingMode.HALF_UP);
        }

        GrupoPago grupoPago;

        // Caso 1: Usar Grupo Permanente existente
        boolean usandoPermanente = request.getGrupoPagoPermanenteId() != null
                || (request.getCodigoGrupoPermanente() != null && !request.getCodigoGrupoPermanente().isBlank());

        if (usandoPermanente) {
            if (request.getGrupoPagoPermanenteId() != null) {
                grupoPago = grupoPagoRepository.findById(request.getGrupoPagoPermanenteId())
                        .orElseThrow(() -> new ResourceNotFoundException("No se encontró el grupo permanente con ID: " + request.getGrupoPagoPermanenteId()));
            } else {
                grupoPago = grupoPagoRepository.findByCodigoUnico(request.getCodigoGrupoPermanente().trim().toUpperCase())
                        .orElseThrow(() -> new ResourceNotFoundException("No se encontró el grupo permanente con código: " + request.getCodigoGrupoPermanente()));
            }

            if (!Boolean.TRUE.equals(grupoPago.getEsPermanente()) && (grupoPago.getTipoGrupoPago() == null || grupoPago.getTipoGrupoPago().getId() != 2)) {
                throw new BadRequestException("El grupo [" + grupoPago.getCodigoUnico() + "] no es un grupo permanente.");
            }

            grupoPago.setPedidoId(request.getPedidoId());
            if (arenaIdFinal != null && !arenaIdFinal.isBlank()) {
                grupoPago.setArenaId(arenaIdFinal);
            }
            if (request.getNombre() != null && !request.getNombre().isBlank()) {
                grupoPago.setNombre(request.getNombre());
            }
            grupoPago.setValorTotal(valorTotal);
            grupoPago.setTotalPagado(BigDecimal.ZERO);
            grupoPago.setSaldoPendiente(valorTotal);
            grupoPago.setEstadoId(1); // 1: Creado/Activo
            grupoPago.setFechaExpiracion(fechaExpiracion);
            grupoPago.setModalidadDivision(modalidad);
            grupoPago.setCantidadPersonas(numPersonas);
            grupoPago.setMontoPreasignado(cuotaPreasignada);
            grupoPago = grupoPagoRepository.save(grupoPago);

            // Reajustar cuotas a los integrantes existentes del grupo permanente
            List<GrupoPagoUsuario> miembros = grupoPagoUsuarioRepository.findByGrupoPagoId(grupoPago.getId());
            boolean usuarioPresente = false;
            for (GrupoPagoUsuario m : miembros) {
                if (m.getUsuarioId().equals(usuarioId)) {
                    usuarioPresente = true;
                }
                m.setMontoPagado(BigDecimal.ZERO);
                m.setEstadoId(1); // Activo
                m.setMontoAsignado(cuotaPreasignada);
                m.setPorcentaje(porcentajePreasignado);
                grupoPagoUsuarioRepository.save(m);
            }

            if (!usuarioPresente) {
                GrupoPagoUsuario nuevoLider = GrupoPagoUsuario.builder()
                        .grupoPago(grupoPago)
                        .usuarioId(usuarioId)
                        .montoAsignado(cuotaPreasignada)
                        .montoPagado(BigDecimal.ZERO)
                        .porcentaje(porcentajePreasignado)
                        .esLider(true)
                        .estadoId(1)
                        .creacionUsuario(usuarioId)
                        .build();
                grupoPagoUsuarioRepository.save(nuevoLider);
            }
        } else {
            // Caso 2: Crear Nuevo Grupo
            String codigoUnico = generarCodigoUnico();
            TipoGrupoPago tipoGrupoPago = tipoGrupoPagoRepository.findById(request.getTipoGrupoPagoId() != null ? request.getTipoGrupoPagoId() : 1)
                    .orElseGet(() -> tipoGrupoPagoRepository.findById(1).orElse(null));

            boolean esPermanente = Boolean.TRUE.equals(request.getEsPermanente()) || (request.getTipoGrupoPagoId() != null && request.getTipoGrupoPagoId() == 2);

            grupoPago = GrupoPago.builder()
                    .codigoUnico(codigoUnico)
                    .nombre(request.getNombre() != null && !request.getNombre().isBlank()
                            ? request.getNombre()
                            : "Vaca Pedido #" + request.getPedidoId())
                    .arenaId(arenaIdFinal)
                    .pedidoId(request.getPedidoId())
                    .tipoGrupoPago(tipoGrupoPago)
                    .esPermanente(esPermanente)
                    .modalidadDivision(modalidad)
                    .cantidadPersonas(numPersonas)
                    .montoPreasignado(cuotaPreasignada)
                    .fechaExpiracion(fechaExpiracion)
                    .valorTotal(valorTotal)
                    .totalPagado(BigDecimal.ZERO)
                    .saldoPendiente(valorTotal)
                    .estadoId(1) // 1: Creado/Activo
                    .creacionUsuario(usuarioId)
                    .build();

            grupoPago = grupoPagoRepository.save(grupoPago);

            // Crear registro del líder
            GrupoPagoUsuario lider = GrupoPagoUsuario.builder()
                    .grupoPago(grupoPago)
                    .usuarioId(usuarioId)
                    .montoAsignado(cuotaPreasignada)
                    .montoPagado(BigDecimal.ZERO)
                    .porcentaje(porcentajePreasignado)
                    .esLider(true)
                    .estadoId(1) // 1: Pendiente/Activo
                    .creacionUsuario(usuarioId)
                    .build();

            grupoPagoUsuarioRepository.save(lider);

            // Registrar participantes iniciales si fueron enviados
            if (request.getParticipantes() != null && !request.getParticipantes().isEmpty()) {
                for (ParticipanteInicialDto p : request.getParticipantes()) {
                    if (p.getUsuarioId() != null && !p.getUsuarioId().equals(usuarioId)) {
                        BigDecimal cuota = esLibre ? null : (p.getMontoAsignado() != null ? p.getMontoAsignado() : cuotaPreasignada);
                        BigDecimal porc = esLibre ? null : (p.getPorcentaje() != null ? p.getPorcentaje() : porcentajePreasignado);

                        GrupoPagoUsuario part = GrupoPagoUsuario.builder()
                                .grupoPago(grupoPago)
                                .usuarioId(p.getUsuarioId())
                                .montoAsignado(cuota)
                                .montoPagado(BigDecimal.ZERO)
                                .porcentaje(porc)
                                .esLider(false)
                                .estadoId(1)
                                .creacionUsuario(usuarioId)
                                .build();

                        grupoPagoUsuarioRepository.save(part);
                    }
                }
            }
        }

        // Notificar a Ordering Service para vincular el grupo al pedido
        orderingClient.asociarGrupoPago(request.getPedidoId(), grupoPago.getId(), authToken);

        return mapearAGrupoPagoResponse(grupoPago, usuarioId);
    }

    @Override
    @Transactional(readOnly = true)
    public GrupoPagoResponse obtenerPorId(Long id, Long currentUsuarioId) {
        GrupoPago grupoPago = grupoPagoRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Grupo de pago no encontrado con ID: " + id));
        return mapearAGrupoPagoResponse(grupoPago, currentUsuarioId);
    }

    @Override
    @Transactional(readOnly = true)
    public GrupoPagoResponse obtenerPorCodigo(String codigoUnico, Long currentUsuarioId) {
        GrupoPago grupoPago = grupoPagoRepository.findByCodigoUnico(codigoUnico.trim().toUpperCase())
                .orElseThrow(() -> new ResourceNotFoundException("Grupo de pago no encontrado con código: " + codigoUnico));
        return mapearAGrupoPagoResponse(grupoPago, currentUsuarioId);
    }

    @Override
    @Transactional(readOnly = true)
    public GrupoPagoResponse obtenerPorPedidoId(Long pedidoId, Long currentUsuarioId) {
        GrupoPago grupoPago = grupoPagoRepository.findByPedidoId(pedidoId)
                .orElseThrow(() -> new ResourceNotFoundException("No existe un grupo de pago para el pedido: " + pedidoId));
        return mapearAGrupoPagoResponse(grupoPago, currentUsuarioId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<GrupoPagoResponse> listarMisGrupos(Long usuarioId) {
        List<GrupoPagoUsuario> membresias = grupoPagoUsuarioRepository.findByUsuarioId(usuarioId);
        Set<Long> grupoIds = membresias.stream()
                .map(m -> m.getGrupoPago().getId())
                .collect(Collectors.toSet());

        if (grupoIds.isEmpty()) {
            return Collections.emptyList();
        }

        return grupoPagoRepository.findAllById(grupoIds).stream()
                .sorted(Comparator.comparing(GrupoPago::getId).reversed())
                .map(gp -> mapearAGrupoPagoResponse(gp, usuarioId))
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public GrupoPagoResponse unirseAGrupo(String codigoUnico, UnirseGrupoRequest request, Long usuarioId) {
        GrupoPago grupoPago = grupoPagoRepository.findByCodigoUnico(codigoUnico.trim().toUpperCase())
                .orElseThrow(() -> new ResourceNotFoundException("Grupo de pago no encontrado con código: " + codigoUnico));

        return ejecutarUnirse(grupoPago, request, usuarioId);
    }

    @Override
    @Transactional
    public GrupoPagoResponse unirseAGrupoPorId(Long grupoPagoId, UnirseGrupoRequest request, Long usuarioId) {
        GrupoPago grupoPago = grupoPagoRepository.findById(grupoPagoId)
                .orElseThrow(() -> new ResourceNotFoundException("Grupo de pago no encontrado con ID: " + grupoPagoId));

        return ejecutarUnirse(grupoPago, request, usuarioId);
    }

    private GrupoPagoResponse ejecutarUnirse(GrupoPago grupoPago, UnirseGrupoRequest request, Long usuarioId) {
        if (grupoPago.getEstadoId() != 1) {
            throw new BadRequestException("El grupo de pago se encuentra en estado "
                    + resolverNombreEstado(grupoPago.getEstadoId()) + " y no admite nuevos participantes.");
        }

        Optional<GrupoPagoUsuario> existenteOpt = grupoPagoUsuarioRepository.findByGrupoPagoIdAndUsuarioId(grupoPago.getId(), usuarioId);
        if (existenteOpt.isPresent()) {
            GrupoPagoUsuario existente = existenteOpt.get();
            if (existente.getEstadoId() == 3) {
                // Reincorporar participante previamente retirado
                existente.setEstadoId(1);
                grupoPagoUsuarioRepository.save(existente);
            }
            return mapearAGrupoPagoResponse(grupoPago, usuarioId);
        }

        BigDecimal montoAsignado = request != null ? request.getMontoAsignado() : null;
        if (montoAsignado == null && !"LIBRE_PAGO".equalsIgnoreCase(grupoPago.getModalidadDivision())) {
            montoAsignado = grupoPago.getMontoPreasignado();
        }

        GrupoPagoUsuario nuevoMiembro = GrupoPagoUsuario.builder()
                .grupoPago(grupoPago)
                .usuarioId(usuarioId)
                .montoAsignado(montoAsignado)
                .montoPagado(BigDecimal.ZERO)
                .esLider(false)
                .estadoId(1)
                .creacionUsuario(usuarioId)
                .build();

        grupoPagoUsuarioRepository.save(nuevoMiembro);
        return mapearAGrupoPagoResponse(grupoPago, usuarioId);
    }

    @Override
    @Transactional
    public GrupoPagoResponse agregarParticipante(Long grupoPagoId, AgregarParticipanteRequest request, Long currentUsuarioId) {
        GrupoPago grupoPago = grupoPagoRepository.findById(grupoPagoId)
                .orElseThrow(() -> new ResourceNotFoundException("Grupo de pago no encontrado"));

        validarLiderazgo(grupoPago, currentUsuarioId);

        if (grupoPago.getEstadoId() != 1) {
            throw new BadRequestException("No se pueden agregar participantes a un grupo que no está activo.");
        }

        Optional<GrupoPagoUsuario> existente = grupoPagoUsuarioRepository.findByGrupoPagoIdAndUsuarioId(grupoPagoId, request.getUsuarioId());
        if (existente.isPresent()) {
            GrupoPagoUsuario gpu = existente.get();
            if (request.getMontoAsignado() != null) gpu.setMontoAsignado(request.getMontoAsignado());
            if (request.getPorcentaje() != null) gpu.setPorcentaje(request.getPorcentaje());
            gpu.setEstadoId(1);
            grupoPagoUsuarioRepository.save(gpu);
        } else {
            BigDecimal monto = request.getMontoAsignado();
            if (monto == null && !"LIBRE_PAGO".equalsIgnoreCase(grupoPago.getModalidadDivision())) {
                monto = grupoPago.getMontoPreasignado();
            }

            GrupoPagoUsuario nuevo = GrupoPagoUsuario.builder()
                    .grupoPago(grupoPago)
                    .usuarioId(request.getUsuarioId())
                    .montoAsignado(monto)
                    .montoPagado(BigDecimal.ZERO)
                    .porcentaje(request.getPorcentaje())
                    .esLider(Boolean.TRUE.equals(request.getEsLider()))
                    .estadoId(1)
                    .creacionUsuario(currentUsuarioId)
                    .build();
            grupoPagoUsuarioRepository.save(nuevo);
        }

        return mapearAGrupoPagoResponse(grupoPago, currentUsuarioId);
    }

    @Override
    @Transactional
    public GrupoPagoResponse eliminarParticipante(Long grupoPagoId, Long usuarioIdAEliminar, Long currentUsuarioId) {
        GrupoPago grupoPago = grupoPagoRepository.findById(grupoPagoId)
                .orElseThrow(() -> new ResourceNotFoundException("Grupo de pago no encontrado"));

        validarLiderazgo(grupoPago, currentUsuarioId);

        GrupoPagoUsuario gpu = grupoPagoUsuarioRepository.findByGrupoPagoIdAndUsuarioId(grupoPagoId, usuarioIdAEliminar)
                .orElseThrow(() -> new ResourceNotFoundException("El participante no pertenece a este grupo"));

        if (gpu.getMontoPagado() != null && gpu.getMontoPagado().compareTo(BigDecimal.ZERO) > 0) {
            throw new BadRequestException("No se puede eliminar a un participante que ya realizó pagos en la vaca.");
        }

        if (Boolean.TRUE.equals(gpu.getEsLider())) {
            throw new BadRequestException("No se puede retirar al líder del grupo.");
        }

        gpu.setEstadoId(3); // 3: Retirado
        grupoPagoUsuarioRepository.save(gpu);

        return mapearAGrupoPagoResponse(grupoPago, currentUsuarioId);
    }

    @Override
    @Transactional
    public GrupoPagoResponse recalcularCuotas(Long grupoPagoId, RecalcularCuotasRequest request, Long currentUsuarioId) {
        GrupoPago grupoPago = grupoPagoRepository.findById(grupoPagoId)
                .orElseThrow(() -> new ResourceNotFoundException("Grupo de pago no encontrado"));

        validarLiderazgo(grupoPago, currentUsuarioId);

        List<GrupoPagoUsuario> participantesActivos = grupoPagoUsuarioRepository.findByGrupoPagoIdAndEstadoIdNot(grupoPagoId, 3);
        if (participantesActivos.isEmpty()) {
            throw new BadRequestException("No hay participantes activos en el grupo.");
        }

        int cantidad = (request != null && request.getCantidadPersonas() != null && request.getCantidadPersonas() > 0)
                ? request.getCantidadPersonas()
                : participantesActivos.size();

        BigDecimal cuota = grupoPago.getValorTotal().divide(BigDecimal.valueOf(cantidad), 2, RoundingMode.HALF_UP);
        BigDecimal porc = BigDecimal.valueOf(100.0).divide(BigDecimal.valueOf(cantidad), 2, RoundingMode.HALF_UP);

        for (GrupoPagoUsuario gpu : participantesActivos) {
            gpu.setMontoAsignado(cuota);
            gpu.setPorcentaje(porc);
            grupoPagoUsuarioRepository.save(gpu);
        }

        grupoPago.setMontoPreasignado(cuota);
        grupoPago.setCantidadPersonas(cantidad);
        grupoPago.setModalidadDivision("POR_PARTES_IGUALES");
        grupoPago = grupoPagoRepository.save(grupoPago);

        return mapearAGrupoPagoResponse(grupoPago, currentUsuarioId);
    }

    @Override
    @Transactional
    public PaymentInitResponse pagarCuotaGrupo(Long grupoPagoId, PagarCuotaGrupoRequest request, String arenaId, Long usuarioId) {
        GrupoPago grupoPago = grupoPagoRepository.findById(grupoPagoId)
                .orElseThrow(() -> new ResourceNotFoundException("Grupo de pago no encontrado"));

        if (grupoPago.getEstadoId() != 1) {
            throw new BadRequestException("El grupo de pago no está activo (Estado: " + resolverNombreEstado(grupoPago.getEstadoId()) + ")");
        }

        if (grupoPago.getSaldoPendiente() != null && grupoPago.getSaldoPendiente().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BadRequestException("La vaca ya se encuentra 100% pagada.");
        }

        // Modalidad: LIBRE_PAGO vs POR_PARTES_IGUALES
        boolean esLibre = "LIBRE_PAGO".equalsIgnoreCase(grupoPago.getModalidadDivision());

        // Obtener o auto-unir al participante
        GrupoPagoUsuario gpu = grupoPagoUsuarioRepository.findByGrupoPagoIdAndUsuarioId(grupoPagoId, usuarioId)
                .orElseGet(() -> {
                    BigDecimal montoAsignado = esLibre ? null : grupoPago.getMontoPreasignado();
                    GrupoPagoUsuario nuevo = GrupoPagoUsuario.builder()
                            .grupoPago(grupoPago)
                            .usuarioId(usuarioId)
                            .montoAsignado(montoAsignado)
                            .montoPagado(BigDecimal.ZERO)
                            .esLider(false)
                            .estadoId(1)
                            .creacionUsuario(usuarioId)
                            .build();
                    return grupoPagoUsuarioRepository.save(nuevo);
                });

        BigDecimal montoAPagar = request != null ? request.getMonto() : null;

        if (esLibre) {
            // Cuando es libre pago, el usuario DEBE asignar el valor dentro del rango del saldo pendiente del pedido
            if (montoAPagar == null || montoAPagar.compareTo(BigDecimal.ZERO) <= 0) {
                throw new BadRequestException("En modalidad de libre pago debe ingresar el monto a aportar (entre $0.01 y $" + grupoPago.getSaldoPendiente() + ").");
            }
            if (grupoPago.getSaldoPendiente() != null && montoAPagar.compareTo(grupoPago.getSaldoPendiente()) > 0) {
                throw new BadRequestException("El monto a aportar ($" + montoAPagar + ") no puede superar el saldo pendiente del pedido ($" + grupoPago.getSaldoPendiente() + ").");
            }
        } else {
            // Cuando es por partes iguales, el valor está preasignado por el sistema
            if (montoAPagar == null) {
                BigDecimal cuota = gpu.getMontoAsignado() != null ? gpu.getMontoAsignado() : grupoPago.getMontoPreasignado();
                BigDecimal pagado = gpu.getMontoPagado() != null ? gpu.getMontoPagado() : BigDecimal.ZERO;
                montoAPagar = (cuota != null) ? cuota.subtract(pagado).max(BigDecimal.ZERO) : grupoPago.getSaldoPendiente();
            }

            if (montoAPagar == null || montoAPagar.compareTo(BigDecimal.ZERO) <= 0) {
                throw new BadRequestException("No tiene saldo pendiente por pagar en su cuota preasignada.");
            }

            // Validar que el monto no exceda el saldo pendiente global del grupo
            if (grupoPago.getSaldoPendiente() != null && montoAPagar.compareTo(grupoPago.getSaldoPendiente()) > 0) {
                montoAPagar = grupoPago.getSaldoPendiente();
                log.info("El monto solicitado excede el saldo pendiente. Ajustado al saldo pendiente: {}", montoAPagar);
            }
        }

        // Construir solicitud hacia PaymentService
        InitPaymentRequest initReq = new InitPaymentRequest();
        initReq.setPedidoId(grupoPago.getPedidoId());
        initReq.setMonto(montoAPagar);
        initReq.setTipoPagoId(2); // 2: PAGO_GRUPAL
        initReq.setMedioPagoId(request != null && request.getMedioPagoId() != null ? request.getMedioPagoId() : 1);
        initReq.setGrupoPagoUsuarioId(gpu.getId());
        initReq.setReturnUrl(request != null ? request.getReturnUrl() : null);
        initReq.setFailUrl(request != null ? request.getFailUrl() : null);
        initReq.setDescription(request != null && request.getDescription() != null && !request.getDescription().isBlank()
                ? request.getDescription()
                : "Aporte Vaca " + grupoPago.getNombre() + " (" + grupoPago.getCodigoUnico() + ")");
        initReq.setExtraParams(request != null ? request.getExtraParams() : null);

        String arenaFinal = (arenaId != null && !arenaId.isBlank()) ? arenaId : grupoPago.getArenaId();
        return paymentService.initiatePayment(initReq, arenaFinal, usuarioId);
    }

    @Override
    @Transactional
    public GrupoPagoResponse cancelarGrupo(Long grupoPagoId, Long currentUsuarioId) {
        GrupoPago grupoPago = grupoPagoRepository.findById(grupoPagoId)
                .orElseThrow(() -> new ResourceNotFoundException("Grupo de pago no encontrado"));

        validarLiderazgo(grupoPago, currentUsuarioId);

        if (grupoPago.getTotalPagado() != null && grupoPago.getTotalPagado().compareTo(BigDecimal.ZERO) > 0) {
            throw new BadRequestException("No se puede cancelar el grupo porque ya existen aportes pagados. Debe solicitarse reembolso.");
        }

        grupoPago.setEstadoId(4); // 4: Cancelado
        grupoPago = grupoPagoRepository.save(grupoPago);

        return mapearAGrupoPagoResponse(grupoPago, currentUsuarioId);
    }

    private void validarLiderazgo(GrupoPago grupoPago, Long usuarioId) {
        if (grupoPago.getCreacionUsuario() != null && !grupoPago.getCreacionUsuario().equals(usuarioId)) {
            // Verificar si tiene flag de líder
            boolean esLider = grupoPagoUsuarioRepository.findByGrupoPagoIdAndUsuarioId(grupoPago.getId(), usuarioId)
                    .map(gpu -> Boolean.TRUE.equals(gpu.getEsLider()))
                    .orElse(false);
            if (!esLider) {
                throw new ForbiddenException("Solo el líder o creador del grupo puede realizar esta acción.");
            }
        }
    }

    private String generarCodigoUnico() {
        String codigo;
        do {
            codigo = "VACA-" + UUID.randomUUID().toString().replace("-", "").substring(0, 6).toUpperCase();
        } while (grupoPagoRepository.findByCodigoUnico(codigo).isPresent());
        return codigo;
    }

    private String normalizarModalidadDivision(String tipo) {
        if (tipo == null || tipo.isBlank()) {
            return "POR_PARTES_IGUALES";
        }
        String t = tipo.trim().toUpperCase();
        if (t.contains("LIBRE")) {
            return "LIBRE_PAGO";
        }
        return "POR_PARTES_IGUALES";
    }

    private GrupoPagoResponse mapearAGrupoPagoResponse(GrupoPago gp, Long currentUsuarioId) {
        List<GrupoPagoUsuario> usuarios = grupoPagoUsuarioRepository.findByGrupoPagoId(gp.getId());
        List<PedidoPago> pagos = pedidoPagoRepository.findByGrupoPagoUsuario_GrupoPago_Id(gp.getId());

        List<GrupoPagoUsuarioResponse> participantesDtos = usuarios.stream()
                .map(this::mapearUsuarioResponse)
                .collect(Collectors.toList());

        GrupoPagoUsuarioResponse miParticipacion = null;
        boolean esLider = false;
        if (currentUsuarioId != null) {
            miParticipacion = usuarios.stream()
                    .filter(u -> u.getUsuarioId().equals(currentUsuarioId))
                    .findFirst()
                    .map(this::mapearUsuarioResponse)
                    .orElse(null);

            esLider = gp.getCreacionUsuario() != null && gp.getCreacionUsuario().equals(currentUsuarioId);
            if (!esLider && miParticipacion != null) {
                esLider = Boolean.TRUE.equals(miParticipacion.getEsLider());
            }
        }

        List<PagoGrupoItemResponse> pagosDtos = pagos.stream()
                .map(p -> PagoGrupoItemResponse.builder()
                        .pedidoPagoId(p.getId())
                        .usuarioId(p.getUsuarioId())
                        .monto(p.getMonto())
                        .fechaPago(p.getFechaPago())
                        .estadoId(p.getEstadoId())
                        .estadoNombre(resolverNombreEstadoPago(p.getEstadoId()))
                        .referenciaPago(p.getReferenciaPago())
                        .authCode(p.getAuthCode())
                        .build())
                .collect(Collectors.toList());

        BigDecimal porcentajeAvance = BigDecimal.ZERO;
        if (gp.getValorTotal() != null && gp.getValorTotal().compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal totalPagado = gp.getTotalPagado() != null ? gp.getTotalPagado() : BigDecimal.ZERO;
            porcentajeAvance = totalPagado.multiply(BigDecimal.valueOf(100))
                    .divide(gp.getValorTotal(), 2, RoundingMode.HALF_UP);
        }

        String modalidad = gp.getModalidadDivision() != null ? gp.getModalidadDivision() : "POR_PARTES_IGUALES";
        boolean esLibre = "LIBRE_PAGO".equalsIgnoreCase(modalidad);

        BigDecimal saldoPendiente = gp.getSaldoPendiente() != null ? gp.getSaldoPendiente() : BigDecimal.ZERO;
        BigDecimal rangoMinimo;
        BigDecimal rangoMaximo;

        if (esLibre) {
            rangoMinimo = new BigDecimal("0.01");
            rangoMaximo = saldoPendiente.compareTo(BigDecimal.ZERO) > 0 ? saldoPendiente : gp.getValorTotal();
        } else {
            BigDecimal cuotaPre = gp.getMontoPreasignado() != null ? gp.getMontoPreasignado() : saldoPendiente;
            rangoMinimo = cuotaPre;
            rangoMaximo = cuotaPre;
        }

        String baseUrl = resolverBaseUrl();
        String qrCodeData = baseUrl + "/vaca/" + gp.getCodigoUnico();
        String qrCodeImage = qrCodeGeneratorService != null ? qrCodeGeneratorService.generateQrCodeBase64(qrCodeData, 300, 300) : null;

        return GrupoPagoResponse.builder()
                .id(gp.getId())
                .codigoUnico(gp.getCodigoUnico())
                .nombre(gp.getNombre())
                .arenaId(gp.getArenaId())
                .pedidoId(gp.getPedidoId())
                .tipoGrupoPagoId(gp.getTipoGrupoPago() != null ? gp.getTipoGrupoPago().getId() : 1)
                .tipoGrupoPagoNombre(gp.getTipoGrupoPago() != null ? gp.getTipoGrupoPago().getNombre() : "TEMPORAL_DIA")
                .esPermanente(gp.getEsPermanente())
                .fechaExpiracion(gp.getFechaExpiracion())
                .valorTotal(gp.getValorTotal())
                .totalPagado(gp.getTotalPagado())
                .saldoPendiente(gp.getSaldoPendiente())
                .porcentajeAvance(porcentajeAvance)
                .estadoId(gp.getEstadoId())
                .estadoNombre(resolverNombreEstado(gp.getEstadoId()))
                .esLider(esLider)
                .modalidadDivision(modalidad)
                .montoPreasignado(gp.getMontoPreasignado())
                .cantidadPersonas(gp.getCantidadPersonas())
                .rangoAporteMinimo(rangoMinimo)
                .rangoAporteMaximo(rangoMaximo)
                .qrCodeData(qrCodeData)
                .qrCodeImage(qrCodeImage)
                .enlaceInvitacion("/vaca/" + gp.getCodigoUnico())
                .miParticipacion(miParticipacion)
                .participantes(participantesDtos)
                .pagos(pagosDtos)
                .creacionFecha(gp.getCreacionFecha())
                .build();
    }

    private GrupoPagoUsuarioResponse mapearUsuarioResponse(GrupoPagoUsuario u) {
        BigDecimal asignado = u.getMontoAsignado();
        BigDecimal pagado = u.getMontoPagado() != null ? u.getMontoPagado() : BigDecimal.ZERO;
        BigDecimal saldo = asignado != null ? asignado.subtract(pagado).max(BigDecimal.ZERO) : BigDecimal.ZERO;

        return GrupoPagoUsuarioResponse.builder()
                .id(u.getId())
                .grupoPagoId(u.getGrupoPago() != null ? u.getGrupoPago().getId() : null)
                .usuarioId(u.getUsuarioId())
                .montoAsignado(asignado)
                .montoPagado(pagado)
                .saldoPendiente(saldo)
                .porcentaje(u.getPorcentaje())
                .esLider(u.getEsLider())
                .estadoId(u.getEstadoId())
                .estadoNombre(resolverNombreEstadoUsuario(u.getEstadoId()))
                .creacionFecha(u.getCreacionFecha())
                .build();
    }

    private String resolverNombreEstado(Integer estadoId) {
        if (estadoId == null) return "DESCONOCIDO";
        return switch (estadoId) {
            case 1 -> "ACTIVO";
            case 2 -> "COMPLETADO";
            case 3 -> "EXPIRADO";
            case 4 -> "CANCELADO";
            default -> "ESTADO_" + estadoId;
        };
    }

    private String resolverNombreEstadoUsuario(Integer estadoId) {
        if (estadoId == null) return "DESCONOCIDO";
        return switch (estadoId) {
            case 1 -> "PENDIENTE";
            case 2 -> "PAGADO";
            case 3 -> "RETIRADO";
            default -> "ESTADO_" + estadoId;
        };
    }

    private String resolverNombreEstadoPago(Integer estadoId) {
        if (estadoId == null) return "DESCONOCIDO";
        return switch (estadoId) {
            case 1 -> "INICIADO";
            case 2 -> "APROBADO";
            case 3 -> "RECHAZADO";
            case 4 -> "REVERSADO";
            case 5 -> "ANULADO";
            default -> "ESTADO_" + estadoId;
        };
    }

    private String resolverBaseUrl() {
        if (httpRequest != null) {
            String origin = httpRequest.getHeader("Origin");
            if (origin != null && !origin.isBlank() && !origin.contains("localhost")) {
                return origin.replaceAll("/+$", "");
            }
            String xForwardedHost = httpRequest.getHeader("X-Forwarded-Host");
            if (xForwardedHost != null && !xForwardedHost.isBlank()) {
                String proto = httpRequest.getHeader("X-Forwarded-Proto");
                String scheme = (proto != null && !proto.isBlank()) ? proto : "https";
                return scheme + "://" + xForwardedHost.replaceAll("/+$", "");
            }
            String referer = httpRequest.getHeader("Referer");
            if (referer != null && !referer.isBlank() && !referer.contains("localhost")) {
                try {
                    java.net.URI uri = new java.net.URI(referer);
                    return uri.getScheme() + "://" + uri.getAuthority();
                } catch (Exception ignored) {}
            }
        }
        if (pwaBaseUrl != null && !pwaBaseUrl.isBlank() && !pwaBaseUrl.contains("localhost")) {
            return pwaBaseUrl.replaceAll("/+$", "");
        }
        return (pwaBaseUrl != null && !pwaBaseUrl.isBlank()) ? pwaBaseUrl : "https://delivery-daviarena-stg.beat-hub.com";
    }
}

