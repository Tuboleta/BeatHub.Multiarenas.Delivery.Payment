package com.beathub.multiarenas.delivery.payment.service.impl;

import com.beathub.multiarenas.delivery.payment.client.CredibancoRestClient;
import com.beathub.multiarenas.delivery.payment.client.OrderingServiceClient;
import com.beathub.multiarenas.delivery.payment.client.dto.*;
import com.beathub.multiarenas.delivery.payment.config.CredibancoProperties;
import com.beathub.multiarenas.delivery.payment.dto.request.*;
import com.beathub.multiarenas.delivery.payment.dto.response.*;
import com.beathub.multiarenas.delivery.payment.entity.*;
import com.beathub.multiarenas.delivery.payment.exception.CredibancoApiException;
import com.beathub.multiarenas.delivery.payment.exception.PaymentException;
import com.beathub.multiarenas.delivery.payment.exception.ResourceNotFoundException;
import com.beathub.multiarenas.delivery.payment.repository.*;
import com.beathub.multiarenas.delivery.payment.service.HmacSignatureService;
import com.beathub.multiarenas.delivery.payment.service.PaymentService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentServiceImpl implements PaymentService {

    private final PedidoPagoRepository pedidoPagoRepository;
    private final TipoPagoRepository tipoPagoRepository;
    private final MedioPagoRepository medioPagoRepository;
    private final GrupoPagoUsuarioRepository grupoPagoUsuarioRepository;
    private final GrupoPagoRepository grupoPagoRepository;
    private final ClientePagoRepository clientePagoRepository;
    private final TarjetaClientePagoRepository tarjetaClientePagoRepository;
    private final CredibancoRestClient credibancoClient;
    private final OrderingServiceClient orderingClient;
    private final CredibancoProperties credibancoProperties;
    private final HmacSignatureService hmacSignatureService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    @Transactional
    public PaymentInitResponse initiatePayment(InitPaymentRequest request, String arenaId, Long usuarioId) {
        // Generar referencia única de pago
        String referenciaPago = "PAY-" + System.currentTimeMillis() + "-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();

        TipoPago tipoPago = tipoPagoRepository.findById(request.getTipoPagoId())
                .orElseGet(() -> tipoPagoRepository.findById(1).orElse(null));

        MedioPago medioPago = request.getMedioPagoId() != null
                ? medioPagoRepository.findById(request.getMedioPagoId()).orElse(null)
                : null;

        GrupoPagoUsuario grupoPagoUsuario = request.getGrupoPagoUsuarioId() != null
                ? grupoPagoUsuarioRepository.findById(request.getGrupoPagoUsuarioId()).orElse(null)
                : null;

        String jsonParamsStr = null;
        if (request.getExtraParams() != null && !request.getExtraParams().isEmpty()) {
            try {
                jsonParamsStr = objectMapper.writeValueAsString(request.getExtraParams());
            } catch (Exception e) {
                log.warn("No se pudo serializar extraParams: {}", e.getMessage());
            }
        }

        // Guardar registro inicial en base de datos
        PedidoPago pedidoPago = PedidoPago.builder()
                .pedidoId(request.getPedidoId())
                .arenaId(arenaId)
                .usuarioId(usuarioId)
                .tipoPago(tipoPago)
                .medioPago(medioPago)
                .grupoPagoUsuario(grupoPagoUsuario)
                .pasarelaId(2) // 2: Credibanco
                .monto(request.getMonto())
                .moneda("COP")
                .referenciaPago(referenciaPago)
                .jsonParams(jsonParamsStr)
                .estadoId(1) // 1: Iniciado
                .creacionUsuario(usuarioId)
                .build();

        pedidoPago = pedidoPagoRepository.save(pedidoPago);

        // Llamar a Credibanco register.do
        CredibancoRegisterResponse credibancoResp = credibancoClient.registerOrder(
                referenciaPago,
                request.getMonto(),
                request.getReturnUrl(),
                request.getFailUrl(),
                request.getDescription() != null ? request.getDescription() : "Pedido BeatHub #" + request.getPedidoId(),
                jsonParamsStr,
                String.valueOf(usuarioId),
                arenaId,
                usuarioId
        );

        if (!credibancoResp.isSuccessful()) {
            pedidoPago.setEstadoId(3); // 3: Rechazado
            pedidoPago.setErrorCode(credibancoResp.getErrorCode());
            pedidoPago.setErrorMessage(credibancoResp.getErrorMessage());
            pedidoPagoRepository.save(pedidoPago);

            throw new CredibancoApiException(credibancoResp.getErrorCode(),
                    "Error registrando orden en Credibanco: " + credibancoResp.getErrorMessage());
        }

        // Actualizar datos devueltos por Credibanco
        pedidoPago.setCredibancoOrderId(credibancoResp.getOrderId());
        pedidoPago.setFormUrl(credibancoResp.getFormUrl());
        pedidoPagoRepository.save(pedidoPago);

        return PaymentInitResponse.builder()
                .pedidoPagoId(pedidoPago.getId())
                .pedidoId(pedidoPago.getPedidoId())
                .referenciaPago(referenciaPago)
                .credibancoOrderId(credibancoResp.getOrderId())
                .formUrl(credibancoResp.getFormUrl())
                .monto(pedidoPago.getMonto())
                .moneda(pedidoPago.getMoneda())
                .estadoId(pedidoPago.getEstadoId())
                .estadoDescripcion("INICIADO")
                .build();
    }

    @Override
    @Transactional
    public PaymentStatusResponse queryPaymentStatus(Long pedidoPagoId, String arenaId, Long usuarioId) {
        PedidoPago pedidoPago = pedidoPagoRepository.findById(pedidoPagoId)
                .orElseThrow(() -> new ResourceNotFoundException("PedidoPago con ID " + pedidoPagoId + " no encontrado"));

        return syncAndMapPaymentStatus(pedidoPago, arenaId, usuarioId);
    }

    @Override
    @Transactional
    public PaymentStatusResponse queryPaymentStatusByReference(String referenciaPago, String arenaId, Long usuarioId) {
        PedidoPago pedidoPago = pedidoPagoRepository.findByReferenciaPago(referenciaPago)
                .orElseThrow(() -> new ResourceNotFoundException("PedidoPago con referencia " + referenciaPago + " no encontrado"));

        return syncAndMapPaymentStatus(pedidoPago, arenaId, usuarioId);
    }

    private PaymentStatusResponse syncAndMapPaymentStatus(PedidoPago pedidoPago, String arenaId, Long usuarioId) {
        // Consultar estado en tiempo real en Credibanco
        if (pedidoPago.getCredibancoOrderId() != null) {
            try {
                CredibancoStatusResponse statusResp = credibancoClient.getOrderStatusExtended(
                        pedidoPago.getCredibancoOrderId(),
                        pedidoPago.getReferenciaPago(),
                        arenaId,
                        usuarioId
                );

                if (statusResp != null) {
                    pedidoPago.setActionCode(statusResp.getActionCode());
                    pedidoPago.setActionCodeDescription(statusResp.getActionCodeDescription());
                    pedidoPago.setAuthCode(statusResp.getAuthCode());
                    pedidoPago.setErrorCode(statusResp.getErrorCode());
                    pedidoPago.setErrorMessage(statusResp.getErrorMessage());

                    if (statusResp.isApproved()) {
                        onPaymentApproved(pedidoPago, "Pago confirmado en Credibanco (authCode: " + statusResp.getAuthCode() + ")");
                        if (statusResp.getBindingId() != null && pedidoPago.getUsuarioId() != null) {
                            try {
                                saveCardBinding(SaveCardBindingRequest.builder()
                                        .bindingId(statusResp.getBindingId())
                                        .tarjetaEnmascarada(statusResp.getMaskedPan())
                                        .franquicia(statusResp.getPaymentSystem())
                                        .expiracion(statusResp.getExpiration())
                                        .titular(statusResp.getCardholderName())
                                        .esPredeterminada(true)
                                        .build(), arenaId, pedidoPago.getUsuarioId());
                            } catch (Exception ex) {
                                log.warn("No se pudo autoguardar tarjeta vinculada tras pago: {}", ex.getMessage());
                            }
                        }
                    } else if (statusResp.getOrderStatus() != null && statusResp.getOrderStatus() == 6) {
                        pedidoPago.setEstadoId(3); // 3: Rechazado
                        pedidoPagoRepository.save(pedidoPago);
                    } else {
                        pedidoPagoRepository.save(pedidoPago);
                    }
                }
            } catch (Exception e) {
                log.warn("No se pudo consultar estado extendido en Credibanco para {}: {}", pedidoPago.getReferenciaPago(), e.getMessage());
            }
        }

        return PaymentStatusResponse.builder()
                .pedidoPagoId(pedidoPago.getId())
                .pedidoId(pedidoPago.getPedidoId())
                .referenciaPago(pedidoPago.getReferenciaPago())
                .credibancoOrderId(pedidoPago.getCredibancoOrderId())
                .mdOrder(pedidoPago.getMdOrder())
                .monto(pedidoPago.getMonto())
                .moneda(pedidoPago.getMoneda())
                .estadoId(pedidoPago.getEstadoId())
                .estadoNombre(mapEstadoNombre(pedidoPago.getEstadoId()))
                .actionCode(pedidoPago.getActionCode())
                .actionCodeDescription(pedidoPago.getActionCodeDescription())
                .authCode(pedidoPago.getAuthCode())
                .errorCode(pedidoPago.getErrorCode())
                .errorMessage(pedidoPago.getErrorMessage())
                .fechaPago(pedidoPago.getFechaPago())
                .build();
    }

    @Override
    @Transactional
    public void processCredibancoCallback(String mdOrder, String orderNumber, String operation, Integer status, String checksum, String signAlias) {
        log.info("Procesando callback de Credibanco: mdOrder={}, orderNumber={}, operation={}, status={}",
                mdOrder, orderNumber, operation, status);

        // Buscar transacción por referencia o mdOrder
        PedidoPago pedidoPago = null;
        if (orderNumber != null) {
            pedidoPago = pedidoPagoRepository.findByReferenciaPago(orderNumber).orElse(null);
        }
        if (pedidoPago == null && mdOrder != null) {
            pedidoPago = pedidoPagoRepository.findByCredibancoOrderId(mdOrder).orElse(null);
        }

        if (pedidoPago == null) {
            log.error("Pedido de pago no encontrado para callback: orderNumber={}, mdOrder={}", orderNumber, mdOrder);
            return;
        }

        pedidoPago.setMdOrder(mdOrder);

        if ("deposited".equalsIgnoreCase(operation) || "approved".equalsIgnoreCase(operation)) {
            if (Integer.valueOf(1).equals(status)) {
                onPaymentApproved(pedidoPago, "Pago aprobado via webhook Credibanco (" + operation + ")");
            } else {
                pedidoPago.setEstadoId(3); // Rechazado
                pedidoPagoRepository.save(pedidoPago);
            }
        } else if ("reversed".equalsIgnoreCase(operation)) {
            pedidoPago.setEstadoId(4); // Reversado
            pedidoPagoRepository.save(pedidoPago);
            orderingClient.updateOrderStatus(pedidoPago.getPedidoId(), 4, "Pago reversado via webhook Credibanco", null);
        } else if ("refunded".equalsIgnoreCase(operation)) {
            pedidoPago.setEstadoId(5); // Anulado/Reembolsado
            pedidoPagoRepository.save(pedidoPago);
            orderingClient.updateOrderStatus(pedidoPago.getPedidoId(), 5, "Pago anulado/reembolsado via webhook Credibanco", null);
        }
    }

    private void onPaymentApproved(PedidoPago pedidoPago, String detailMessage) {
        pedidoPago.setEstadoId(2); // 2: Aprobado / Deposited
        pedidoPago.setFechaPago(LocalDateTime.now());
        pedidoPagoRepository.save(pedidoPago);

        if (pedidoPago.getGrupoPagoUsuario() != null) {
            GrupoPagoUsuario gpu = pedidoPago.getGrupoPagoUsuario();
            BigDecimal pagadoPrevio = gpu.getMontoPagado() != null ? gpu.getMontoPagado() : BigDecimal.ZERO;
            gpu.setMontoPagado(pagadoPrevio.add(pedidoPago.getMonto()));

            if (gpu.getMontoAsignado() != null && gpu.getMontoPagado().compareTo(gpu.getMontoAsignado()) >= 0) {
                gpu.setEstadoId(2); // 2: Pagado
            }
            grupoPagoUsuarioRepository.save(gpu);

            GrupoPago gp = gpu.getGrupoPago();
            if (gp != null) {
                BigDecimal totalPagadoPrevio = gp.getTotalPagado() != null ? gp.getTotalPagado() : BigDecimal.ZERO;
                BigDecimal nuevoTotalPagado = totalPagadoPrevio.add(pedidoPago.getMonto());
                gp.setTotalPagado(nuevoTotalPagado);

                BigDecimal valorTotal = gp.getValorTotal() != null ? gp.getValorTotal() : BigDecimal.ZERO;
                BigDecimal nuevoSaldo = valorTotal.subtract(nuevoTotalPagado).max(BigDecimal.ZERO);
                gp.setSaldoPendiente(nuevoSaldo);

                if (nuevoSaldo.compareTo(BigDecimal.ZERO) <= 0) {
                    gp.setEstadoId(2); // 2: Completado (100% Pagado)
                    orderingClient.updateOrderStatus(pedidoPago.getPedidoId(), 2,
                            "Vaca/Pago grupal 100% completado (" + detailMessage + ")", null);
                } else {
                    log.info("Aporte a la vaca registrado para pedido {}. Total recaudado: {}, Saldo pendiente: {}",
                            pedidoPago.getPedidoId(), nuevoTotalPagado, nuevoSaldo);
                }
                grupoPagoRepository.save(gp);
            }
        } else {
            // Notificar a Ordering (estado 2: Pagado / En preparación)
            orderingClient.updateOrderStatus(pedidoPago.getPedidoId(), 2, detailMessage, null);
        }
    }

    @Override
    @Transactional
    public RefundResponse refundPayment(RefundPaymentRequest request, String arenaId, Long usuarioId) {
        PedidoPago pedidoPago = pedidoPagoRepository.findById(request.getPedidoPagoId())
                .orElseThrow(() -> new ResourceNotFoundException("PedidoPago no encontrado"));

        if (pedidoPago.getCredibancoOrderId() == null) {
            throw new PaymentException("El pago no tiene un identificador de orden en Credibanco para reembolsar");
        }

        CredibancoRefundResponse refundResp = credibancoClient.refundOrder(
                pedidoPago.getCredibancoOrderId(),
                request.getMonto() != null ? request.getMonto() : pedidoPago.getMonto(),
                arenaId,
                usuarioId
        );

        if (refundResp.isSuccessful()) {
            pedidoPago.setEstadoId(5); // Reembolsado / Anulado
            pedidoPagoRepository.save(pedidoPago);

            orderingClient.updateOrderStatus(pedidoPago.getPedidoId(), 5, "Reembolso procesado exitosamente: " + request.getMotivo(), null);

            return RefundResponse.builder()
                    .pedidoPagoId(pedidoPago.getId())
                    .credibancoOrderId(pedidoPago.getCredibancoOrderId())
                    .montoReembolsado(request.getMonto() != null ? request.getMonto() : pedidoPago.getMonto())
                    .exito(true)
                    .mensaje("Reembolso procesado exitosamente en Credibanco")
                    .build();
        } else {
            return RefundResponse.builder()
                    .pedidoPagoId(pedidoPago.getId())
                    .credibancoOrderId(pedidoPago.getCredibancoOrderId())
                    .exito(false)
                    .mensaje("Error al reembolsar en Credibanco: " + refundResp.getErrorMessage())
                    .build();
        }
    }

    @Override
    public VerifyCardResponse verifyCard(VerifyCardRequest request, String arenaId, Long usuarioId) {
        CredibancoVerifyCardResponse verifyResp = credibancoClient.verifyCard(
                request.getPan(),
                request.getCvc(),
                request.getExpiry(),
                arenaId,
                usuarioId
        );

        return VerifyCardResponse.builder()
                .valida(verifyResp.isSuccessful())
                .orderId(verifyResp.getOrderId())
                .authCode(verifyResp.getAuthCode())
                .actionCode(verifyResp.getActionCode())
                .actionCodeDescription(verifyResp.getActionCodeDescription())
                .mensaje(verifyResp.getUserMessage() != null ? verifyResp.getUserMessage() : verifyResp.getErrorMessage())
                .build();
    }

    @Override
    @Transactional
    public PaymentTokenResponse payWithToken(PayWithTokenRequest request, String arenaId, Long usuarioId, String ipCliente) {
        log.info("Iniciando pago con tarjeta tokenizada para usuario {}, pedido {}, monto {}",
                usuarioId, request.getPedidoId(), request.getMonto());

        // 1. Obtener datos de la tarjeta tokenizada
        String bindingId = request.getBindingId();
        TarjetaClientePago tarjetaCliente = null;
        if (bindingId.matches("\\d+")) {
            tarjetaCliente = tarjetaClientePagoRepository
                    .findByIdAndClientePagoArenaUsuarioId(Long.valueOf(bindingId), usuarioId)
                    .orElse(null);
        }

        String realBindingId = bindingId;
        String tarjetaEnmascarada = null;
        if (tarjetaCliente != null) {
            realBindingId = tarjetaCliente.getTarjetaId() != null ? tarjetaCliente.getTarjetaId() : tarjetaCliente.getClientePago().getBindingId();
            tarjetaEnmascarada = tarjetaCliente.getTarjetaEnmascarada();
        } else {
            ClientePago cp = clientePagoRepository.findByArenaUsuarioIdAndBindingId(usuarioId, bindingId).orElse(null);
            if (cp != null) {
                tarjetaEnmascarada = cp.getTarjetaEnmascarada();
            }
        }

        // 2. Generar referencia única de transacción
        String referenciaPago = "PAY-TOK-" + System.currentTimeMillis() + "-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();

        TipoPago tipoPago = tipoPagoRepository.findById(request.getTipoPagoId() != null ? request.getTipoPagoId() : 1)
                .orElseGet(() -> tipoPagoRepository.findById(1).orElse(null));

        MedioPago medioPago = medioPagoRepository.findById(1).orElse(null); // Tarjetas

        GrupoPagoUsuario grupoPagoUsuario = request.getGrupoPagoUsuarioId() != null
                ? grupoPagoUsuarioRepository.findById(request.getGrupoPagoUsuarioId()).orElse(null)
                : null;

        // 3. Crear registro inicial en base de datos
        PedidoPago pedidoPago = PedidoPago.builder()
                .pedidoId(request.getPedidoId())
                .arenaId(arenaId)
                .usuarioId(usuarioId)
                .tipoPago(tipoPago)
                .medioPago(medioPago)
                .grupoPagoUsuario(grupoPagoUsuario)
                .pasarelaId(2) // 2: Credibanco
                .monto(request.getMonto())
                .moneda("COP")
                .referenciaPago(referenciaPago)
                .ipCliente(ipCliente)
                .estadoId(1) // 1: Iniciado
                .creacionUsuario(usuarioId)
                .build();
        pedidoPago = pedidoPagoRepository.save(pedidoPago);

        // 4. Paso A: Registrar orden en pasarela Credibanco (asociando clientId)
        String description = request.getDescription() != null ? request.getDescription() : "Pago 1-Click Pedido #" + request.getPedidoId();
        CredibancoRegisterResponse regResp = credibancoClient.registerOrder(
                referenciaPago,
                request.getMonto(),
                request.getReturnUrl(),
                request.getFailUrl(),
                description,
                null,
                String.valueOf(usuarioId),
                arenaId,
                usuarioId
        );

        if (!regResp.isSuccessful()) {
            pedidoPago.setEstadoId(3); // 3: Rechazado
            pedidoPago.setErrorCode(regResp.getErrorCode());
            pedidoPago.setErrorMessage(regResp.getErrorMessage());
            pedidoPagoRepository.save(pedidoPago);

            return PaymentTokenResponse.builder()
                    .pedidoPagoId(pedidoPago.getId())
                    .pedidoId(pedidoPago.getPedidoId())
                    .referenciaPago(referenciaPago)
                    .estadoId(3)
                    .estadoNombre("RECHAZADO")
                    .monto(pedidoPago.getMonto())
                    .moneda(pedidoPago.getMoneda())
                    .mensaje("Error al registrar orden en Credibanco: " + regResp.getErrorMessage())
                    .requiere3ds(false)
                    .build();
        }

        String credibancoOrderId = regResp.getOrderId();
        pedidoPago.setCredibancoOrderId(credibancoOrderId);

        // 5. Paso B: Ejecutar débito directo contra la tarjeta tokenizada (paymentOrderBinding.do)
        CredibancoPaymentOrderResponse payResp = credibancoClient.paymentOrderBinding(
                credibancoOrderId,
                realBindingId,
                request.getCvc(),
                ipCliente,
                arenaId,
                usuarioId
        );

        if (payResp != null && payResp.isSuccessful()) {
            pedidoPago.setActionCode("0");
            pedidoPago.setActionCodeDescription("Aprobada");
            pedidoPago.setAuthCode(payResp.getRbsOrderId() != null ? payResp.getRbsOrderId() : "AUTH-" + UUID.randomUUID().toString().substring(0, 6));
            onPaymentApproved(pedidoPago, "Pago con tarjeta tokenizada exitoso (One-Click)");

            return PaymentTokenResponse.builder()
                    .pedidoPagoId(pedidoPago.getId())
                    .pedidoId(pedidoPago.getPedidoId())
                    .referenciaPago(referenciaPago)
                    .credibancoOrderId(credibancoOrderId)
                    .estadoId(2)
                    .estadoNombre("APROBADO")
                    .monto(pedidoPago.getMonto())
                    .moneda(pedidoPago.getMoneda())
                    .authCode(pedidoPago.getAuthCode())
                    .actionCode("0")
                    .mensaje("Pago procesado exitosamente")
                    .requiere3ds(false)
                    .fechaPago(pedidoPago.getFechaPago())
                    .build();
        } else if (payResp != null && payResp.is3dsRequired()) {
            pedidoPago.setFormUrl(payResp.getAcsUrl());
            pedidoPagoRepository.save(pedidoPago);

            return PaymentTokenResponse.builder()
                    .pedidoPagoId(pedidoPago.getId())
                    .pedidoId(pedidoPago.getPedidoId())
                    .referenciaPago(referenciaPago)
                    .credibancoOrderId(credibancoOrderId)
                    .estadoId(1)
                    .estadoNombre("PENDIENTE_3DS")
                    .monto(pedidoPago.getMonto())
                    .moneda(pedidoPago.getMoneda())
                    .mensaje("Autenticación 3D Secure requerida por el banco emisor")
                    .requiere3ds(true)
                    .redirect3dsUrl(payResp.getAcsUrl())
                    .build();
        } else {
            String errorMsg = payResp != null ? payResp.getErrorMessage() : "Transacción rechazada por la pasarela";
            pedidoPago.setEstadoId(3); // Rechazado
            pedidoPago.setErrorCode(payResp != null ? payResp.getErrorCode() : "DECLINED");
            pedidoPago.setErrorMessage(errorMsg);
            pedidoPagoRepository.save(pedidoPago);

            return PaymentTokenResponse.builder()
                    .pedidoPagoId(pedidoPago.getId())
                    .pedidoId(pedidoPago.getPedidoId())
                    .referenciaPago(referenciaPago)
                    .credibancoOrderId(credibancoOrderId)
                    .estadoId(3)
                    .estadoNombre("RECHAZADO")
                    .monto(pedidoPago.getMonto())
                    .moneda(pedidoPago.getMoneda())
                    .mensaje(errorMsg)
                    .requiere3ds(false)
                    .build();
        }
    }

    @Override
    @Transactional(readOnly = true)
    public java.util.List<TarjetaGuardadaResponse> getUserCards(String arenaId, Long usuarioId) {
        java.util.List<TarjetaClientePago> tarjetas = tarjetaClientePagoRepository
                .findByClientePagoArenaUsuarioIdAndEstadoId(usuarioId, 1);

        if (!tarjetas.isEmpty()) {
            return tarjetas.stream().map(this::mapToTarjetaResponse).toList();
        }

        // Fallback: Si no hay detalle en TarjetaClientePago, consultar ClientePago directamente
        java.util.List<ClientePago> clientesPago = clientePagoRepository.findByArenaUsuarioIdAndEstadoId(usuarioId, 1);
        return clientesPago.stream().map(cp -> TarjetaGuardadaResponse.builder()
                .id(cp.getId())
                .clientePagoId(cp.getId())
                .bindingId(cp.getBindingId())
                .tarjetaEnmascarada(cp.getTarjetaEnmascarada() != null ? cp.getTarjetaEnmascarada() : "**** **** **** ****")
                .franquicia("TARJETA")
                .esPredeterminada(true)
                .estadoId(cp.getEstadoId())
                .creacionFecha(cp.getCreacionFecha())
                .build()).toList();
    }

    @Override
    @Transactional
    public TarjetaGuardadaResponse saveCardBinding(SaveCardBindingRequest request, String arenaId, Long usuarioId) {
        // 1. Obtener o crear ClientePago
        ClientePago clientePago = clientePagoRepository
                .findByArenaUsuarioIdAndBindingId(usuarioId, request.getBindingId())
                .orElseGet(() -> clientePagoRepository.save(ClientePago.builder()
                        .clienteReferencia("USR-" + usuarioId)
                        .arenaUsuarioId(usuarioId)
                        .arenaId(arenaId)
                        .tarjetaEnmascarada(request.getTarjetaEnmascarada())
                        .bindingId(request.getBindingId())
                        .estadoId(1)
                        .creacionUsuario(usuarioId)
                        .build()));

        // 2. Si es predeterminada, desmarcar las anteriores
        if (Boolean.TRUE.equals(request.getEsPredeterminada())) {
            java.util.List<TarjetaClientePago> previas = tarjetaClientePagoRepository.findByClientePagoArenaUsuarioIdAndEstadoId(usuarioId, 1);
            previas.forEach(t -> t.setEsPredeterminada(false));
            tarjetaClientePagoRepository.saveAll(previas);
        }

        // 3. Crear TarjetaClientePago
        TarjetaClientePago tarjeta = TarjetaClientePago.builder()
                .clientePago(clientePago)
                .tarjetaId(request.getBindingId())
                .tarjetaEnmascarada(request.getTarjetaEnmascarada() != null ? request.getTarjetaEnmascarada() : clientePago.getTarjetaEnmascarada())
                .franquicia(request.getFranquicia() != null ? request.getFranquicia().toUpperCase() : "TARJETA")
                .expiracion(request.getExpiracion())
                .titular(request.getTitular())
                .esPredeterminada(Boolean.TRUE.equals(request.getEsPredeterminada()))
                .estadoId(1)
                .creacionUsuario(usuarioId)
                .build();

        tarjeta = tarjetaClientePagoRepository.save(tarjeta);
        return mapToTarjetaResponse(tarjeta);
    }

    @Override
    @Transactional
    public void deleteUserCard(Long tarjetaId, String arenaId, Long usuarioId) {
        TarjetaClientePago tarjeta = tarjetaClientePagoRepository
                .findByIdAndClientePagoArenaUsuarioId(tarjetaId, usuarioId)
                .orElseThrow(() -> new ResourceNotFoundException("Tarjeta con ID " + tarjetaId + " no encontrada para este usuario"));

        String bindingId = tarjeta.getTarjetaId() != null ? tarjeta.getTarjetaId() : tarjeta.getClientePago().getBindingId();
        if (bindingId != null && !bindingId.isBlank()) {
            try {
                credibancoClient.unBindCard(bindingId, arenaId, usuarioId);
            } catch (Exception e) {
                log.warn("No se pudo desvincular tarjeta en Credibanco: {}", e.getMessage());
            }
        }

        tarjeta.setEstadoId(0); // Inactivo
        tarjeta.setEsPredeterminada(false);
        tarjetaClientePagoRepository.save(tarjeta);

        ClientePago cp = tarjeta.getClientePago();
        if (cp != null) {
            java.util.List<TarjetaClientePago> activas = tarjetaClientePagoRepository.findByClientePagoId(cp.getId())
                    .stream().filter(t -> Integer.valueOf(1).equals(t.getEstadoId())).toList();
            if (activas.isEmpty()) {
                cp.setEstadoId(0);
                clientePagoRepository.save(cp);
            }
        }
    }

    @Override
    @Transactional
    public void setDefaultCard(Long tarjetaId, String arenaId, Long usuarioId) {
        java.util.List<TarjetaClientePago> tarjetas = tarjetaClientePagoRepository
                .findByClientePagoArenaUsuarioIdAndEstadoId(usuarioId, 1);

        boolean found = false;
        for (TarjetaClientePago t : tarjetas) {
            if (t.getId().equals(tarjetaId)) {
                t.setEsPredeterminada(true);
                found = true;
            } else {
                t.setEsPredeterminada(false);
            }
        }

        if (!found) {
            throw new ResourceNotFoundException("Tarjeta con ID " + tarjetaId + " no pertenece a las tarjetas activas del usuario");
        }

        tarjetaClientePagoRepository.saveAll(tarjetas);
    }

    @Override
    @Transactional
    public CredibancoGetBindingsResponse syncCredibancoBindings(String arenaId, Long usuarioId) {
        CredibancoGetBindingsResponse resp = credibancoClient.getBindings(String.valueOf(usuarioId), arenaId, usuarioId);
        if (resp != null && resp.getBindings() != null) {
            for (var item : resp.getBindings()) {
                String bId = item.resolveBindingId();
                if (bId != null && !bId.isBlank()) {
                    SaveCardBindingRequest saveReq = SaveCardBindingRequest.builder()
                            .bindingId(bId)
                            .tarjetaEnmascarada(item.resolveMaskedPan())
                            .franquicia(item.getPaymentSystem())
                            .expiracion(item.resolveExpiry())
                            .esPredeterminada(false)
                            .build();
                    saveCardBinding(saveReq, arenaId, usuarioId);
                }
            }
        }
        return resp;
    }

    private TarjetaGuardadaResponse mapToTarjetaResponse(TarjetaClientePago tarjeta) {
        return TarjetaGuardadaResponse.builder()
                .id(tarjeta.getId())
                .clientePagoId(tarjeta.getClientePago() != null ? tarjeta.getClientePago().getId() : null)
                .bindingId(tarjeta.getTarjetaId() != null ? tarjeta.getTarjetaId() : (tarjeta.getClientePago() != null ? tarjeta.getClientePago().getBindingId() : null))
                .tarjetaEnmascarada(tarjeta.getTarjetaEnmascarada())
                .franquicia(tarjeta.getFranquicia())
                .expiracion(tarjeta.getExpiracion())
                .titular(tarjeta.getTitular())
                .esPredeterminada(tarjeta.getEsPredeterminada())
                .estadoId(tarjeta.getEstadoId())
                .creacionFecha(tarjeta.getCreacionFecha())
                .build();
    }

    private String mapEstadoNombre(Integer estadoId) {
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
}
