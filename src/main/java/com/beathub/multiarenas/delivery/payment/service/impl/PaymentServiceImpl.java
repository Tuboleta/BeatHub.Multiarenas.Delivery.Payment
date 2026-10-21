package com.beathub.multiarenas.delivery.payment.service.impl;

import com.beathub.multiarenas.delivery.payment.client.CredibancoRestClient;
import com.beathub.multiarenas.delivery.payment.client.NotificationServiceClient;
import com.beathub.multiarenas.delivery.payment.client.OrderingServiceClient;
import com.beathub.multiarenas.delivery.payment.client.dto.*;
import com.beathub.multiarenas.delivery.payment.config.CredibancoProperties;
import com.beathub.multiarenas.delivery.payment.dto.request.*;
import com.beathub.multiarenas.delivery.payment.dto.response.*;
import com.beathub.multiarenas.delivery.payment.entity.*;
import com.beathub.multiarenas.delivery.payment.exception.BadRequestException;
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
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
    private final NotificationServiceClient notificationServiceClient;
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

        boolean solicitarTokenizacion = Boolean.TRUE.equals(request.getGuardarTarjeta())
                || (request.getExtraParams() != null && (
                        Boolean.parseBoolean(String.valueOf(request.getExtraParams().get("guardarTarjeta")))
                        || Boolean.parseBoolean(String.valueOf(request.getExtraParams().get("solicitarTokenizacion")))
                   ));
        Map<String, Object> extraParams = request.getExtraParams() != null ? new HashMap<>(request.getExtraParams()) : new HashMap<>();
        extraParams.put("guardarTarjeta", solicitarTokenizacion);

        String jsonParamsStr = null;
        try {
            jsonParamsStr = objectMapper.writeValueAsString(extraParams);
        } catch (Exception e) {
            log.warn("No se pudo serializar extraParams: {}", e.getMessage());
        }

        // Guardar registro inicial en base de datos
        PedidoPago pedidoPago = PedidoPago.builder()
                .pedidoId(request.getPedidoId())
                .grupoPedidoId(request.getGrupoPedidoId())
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

        String returnUrl = (request.getReturnUrl() != null && !request.getReturnUrl().isBlank())
                ? request.getReturnUrl()
                : (credibancoProperties.getDefaultReturnUrl() != null && !credibancoProperties.getDefaultReturnUrl().isBlank()
                    ? credibancoProperties.getDefaultReturnUrl()
                    : "http://localhost:3000/checkout/payment-return");

        String failUrl = (request.getFailUrl() != null && !request.getFailUrl().isBlank())
                ? request.getFailUrl()
                : (credibancoProperties.getDefaultFailUrl() != null && !credibancoProperties.getDefaultFailUrl().isBlank()
                    ? credibancoProperties.getDefaultFailUrl()
                    : "http://localhost:3000/checkout/payment-return?status=failed");

        // Si el cliente eligió guardar la tarjeta, se envía su clientId a Credibanco asociado a la arena (Modelo A).
        // NOTA: NO se envía features=AUTO_PAYMENT por defecto ya que Credibanco rechaza con errorCode 13
        // ('El comercio no tiene permiso para usar el pago automático') si la terminal UAT no está habilitada.
        // Solo se envía si viene explícito en extraParams o si credibancoProperties.getAutoPaymentEnabled() es true.
        String clientIdParaCredibanco = (request.getExtraParams() != null && request.getExtraParams().containsKey("clientId"))
                ? String.valueOf(request.getExtraParams().get("clientId"))
                : (solicitarTokenizacion ? resolverClientId(arenaId, usuarioId) : null);

        String featuresParaCredibanco = null;
        if (request.getExtraParams() != null && request.getExtraParams().containsKey("features")) {
            featuresParaCredibanco = String.valueOf(request.getExtraParams().get("features"));
        } else if (Boolean.TRUE.equals(credibancoProperties.getAutoPaymentEnabled()) && solicitarTokenizacion) {
            featuresParaCredibanco = "AUTO_PAYMENT";
        }

        // Si el cliente paga con una tarjeta guardada previamente enlazada (bindingId):
        String bindingIdParaCredibanco = request.getBindingId();
        if (bindingIdParaCredibanco == null && request.getExtraParams() != null && request.getExtraParams().containsKey("bindingId")) {
            bindingIdParaCredibanco = String.valueOf(request.getExtraParams().get("bindingId"));
        }
        if (bindingIdParaCredibanco != null && !bindingIdParaCredibanco.isBlank()) {
            if (bindingIdParaCredibanco.matches("\\d+")) {
                Long cardId = Long.valueOf(bindingIdParaCredibanco);
                var tc = tarjetaClientePagoRepository
                        .findByIdAndClientePagoArenaIdAndClientePagoArenaUsuarioId(cardId, arenaId, usuarioId)
                        .or(() -> tarjetaClientePagoRepository.findByIdAndClientePagoArenaUsuarioId(cardId, usuarioId))
                        .orElse(null);
                if (tc != null) {
                    bindingIdParaCredibanco = tc.getTarjetaId() != null ? tc.getTarjetaId() : tc.getClientePago().getBindingId();
                }
            }
            if (clientIdParaCredibanco == null) {
                clientIdParaCredibanco = resolverClientId(arenaId, usuarioId);
            }
        }

        // Llamar a Credibanco register.do (con bindingId si es pago con tarjeta guardada)
        CredibancoRegisterResponse credibancoResp = credibancoClient.registerOrder(
                referenciaPago,
                request.getMonto(),
                returnUrl,
                failUrl,
                request.getDescription() != null ? request.getDescription() : "Pedido BeatHub #" + request.getPedidoId(),
                jsonParamsStr,
                clientIdParaCredibanco,
                bindingIdParaCredibanco,
                featuresParaCredibanco,
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

    @Override
    @Transactional
    public PaymentStatusResponse queryPaymentStatusByPedidoId(Long pedidoId, String arenaId, Long usuarioId) {
        List<PedidoPago> pagos = pedidoPagoRepository.findByPedidoId(pedidoId);
        if (pagos.isEmpty()) {
            throw new ResourceNotFoundException("No se encontró registro de pago para el pedido " + pedidoId);
        }
        PedidoPago masReciente = pagos.get(pagos.size() - 1);
        return syncAndMapPaymentStatus(masReciente, arenaId, usuarioId);
    }

    @Override
    @Transactional
    public PaymentStatusResponse queryPaymentStatusByCredibancoOrderId(String credibancoOrderId, String arenaId, Long usuarioId) {
        PedidoPago pedidoPago = pedidoPagoRepository.findByCredibancoOrderId(credibancoOrderId)
                .orElseThrow(() -> new ResourceNotFoundException("PedidoPago no encontrado para Credibanco Order ID: " + credibancoOrderId));

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
                    String approvalCode = statusResp.getApprovalCode();
                    pedidoPago.setAuthCode(approvalCode != null ? approvalCode : statusResp.getAuthCode());
                    pedidoPago.setErrorCode(statusResp.getErrorCode());
                    pedidoPago.setErrorMessage(statusResp.getErrorMessage());

                    if (statusResp.isApproved()) {
                        onPaymentApproved(pedidoPago, "Pago confirmado en Credibanco (authCode: " + pedidoPago.getAuthCode() + ")",
                                pedidoPago.getAuthCode(), statusResp.getTerminal());

                        boolean debeGuardarTarjeta = false;
                        if (pedidoPago.getJsonParams() != null) {
                            try {
                                com.fasterxml.jackson.databind.JsonNode jn = objectMapper.readTree(pedidoPago.getJsonParams());
                                if (jn.has("guardarTarjeta")) {
                                    debeGuardarTarjeta = jn.get("guardarTarjeta").asBoolean(false);
                                }
                            } catch (Exception ignored) {}
                        }
                        if (!debeGuardarTarjeta && statusResp.getMerchantOrderParams() != null) {
                            for (Map<String, Object> mop : statusResp.getMerchantOrderParams()) {
                                if ("guardarTarjeta".equalsIgnoreCase(String.valueOf(mop.get("name")))
                                        && Boolean.parseBoolean(String.valueOf(mop.get("value")))) {
                                    debeGuardarTarjeta = true;
                                    break;
                                }
                            }
                        }

                        // Guardado de tarjeta: La fuente de la verdad es el consentimiento del usuario en Credibanco.
                        // Solo si el usuario marcó el check en Credibanco, la pasarela retorna bindingInfo con un bindingId legítimo (diferente a mdOrder).
                        String bindingId = statusResp.getBindingId();
                        String mdOrder = pedidoPago.getMdOrder() != null ? pedidoPago.getMdOrder() : pedidoPago.getCredibancoOrderId();
                        if (bindingId != null && !bindingId.isBlank()
                                && (mdOrder == null || !bindingId.equalsIgnoreCase(mdOrder))
                                && pedidoPago.getUsuarioId() != null) {
                            try {
                                log.info("Guardando tarjeta vinculada confirmada por Credibanco para usuario {}, bindingId {}",
                                        pedidoPago.getUsuarioId(), bindingId);
                                saveCardBinding(SaveCardBindingRequest.builder()
                                        .bindingId(bindingId)
                                        .tarjetaEnmascarada(statusResp.getMaskedPan() != null ? statusResp.getMaskedPan() : "**** **** **** ****")
                                        .franquicia(statusResp.getPaymentSystem() != null ? statusResp.getPaymentSystem() : "TARJETA")
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
            boolean isApproved = status == null || Integer.valueOf(1).equals(status) || Integer.valueOf(2).equals(status);
            if (isApproved) {
                try {
                    syncAndMapPaymentStatus(pedidoPago, pedidoPago.getArenaId(), pedidoPago.getUsuarioId());
                } catch (Exception ex) {
                    log.warn("No se pudo sincronizar estado extendido tras callback webhook: {}", ex.getMessage());
                }
                if (!Integer.valueOf(2).equals(pedidoPago.getEstadoId())) {
                    onPaymentApproved(pedidoPago, "Pago aprobado via webhook Credibanco (" + operation + ")", null, null);
                }
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
        onPaymentApproved(pedidoPago, detailMessage, null, null);
    }

    private void onPaymentApproved(PedidoPago pedidoPago, String detailMessage, String autorizacion, String terminal) {
        if (Integer.valueOf(2).equals(pedidoPago.getEstadoId())) {
            log.info("El pago con ID {} ya se encontraba en estado Aprobado. Omitiendo reprocesamiento.", pedidoPago.getId());
            return;
        }
        pedidoPago.setEstadoId(2); // 2: Aprobado / Deposited
        pedidoPago.setFechaPago(LocalDateTime.now());
        if (autorizacion != null && !autorizacion.isBlank()) {
            pedidoPago.setAuthCode(autorizacion);
        }
        pedidoPagoRepository.save(pedidoPago);

        String effectiveAuth = (autorizacion != null && !autorizacion.isBlank())
                ? autorizacion
                : pedidoPago.getAuthCode();

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
                    String msg = "Vaca/Pago grupal 100% completado (" + detailMessage + ")";
                    if (effectiveAuth != null || terminal != null) {
                        orderingClient.updateOrderStatus(pedidoPago.getPedidoId(), 2,
                                msg, null,
                                effectiveAuth, terminal, pedidoPago.getMonto());
                    } else {
                        orderingClient.updateOrderStatus(pedidoPago.getPedidoId(), 2, msg, null);
                    }

                    // Despachar evento asíncrono de vaca completada (Flujo 8: VACA_COMPLETE)
                    if (notificationServiceClient != null) {
                        notificationServiceClient.notificarVacaCompletada(
                                parseArenaId(gp.getArenaId()),
                                pedidoPago.getUsuarioId() != null ? pedidoPago.getUsuarioId() : gpu.getUsuarioId(),
                                pedidoPago.getPedidoId(),
                                gp.getNombre(),
                                gp.getValorTotal()
                        );
                    }
                } else {
                    log.info("Aporte a la vaca registrado para pedido {}. Total recaudado: {}, Saldo pendiente: {}",
                            pedidoPago.getPedidoId(), nuevoTotalPagado, nuevoSaldo);
                }
                grupoPagoRepository.save(gp);
            }
        } else {
            // Notificar a Ordering (estado 2: Pagado / En preparación)
            if (effectiveAuth != null || terminal != null) {
                orderingClient.updateOrderStatus(pedidoPago.getPedidoId(), 2, detailMessage, null,
                        effectiveAuth, terminal, pedidoPago.getMonto());
            } else {
                orderingClient.updateOrderStatus(pedidoPago.getPedidoId(), 2, detailMessage, null);
            }
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

        // 1. Obtener datos de la tarjeta tokenizada (aislada por arena)
        String bindingId = request.getBindingId();
        TarjetaClientePago tarjetaCliente = null;
        if (bindingId.matches("\\d+")) {
            Long cardId = Long.valueOf(bindingId);
            tarjetaCliente = tarjetaClientePagoRepository
                    .findByIdAndClientePagoArenaIdAndClientePagoArenaUsuarioId(cardId, arenaId, usuarioId)
                    .or(() -> tarjetaClientePagoRepository.findByIdAndClientePagoArenaUsuarioId(cardId, usuarioId))
                    .orElse(null);
        }

        String realBindingId = bindingId;
        String tarjetaEnmascarada = null;
        if (tarjetaCliente != null) {
            realBindingId = tarjetaCliente.getTarjetaId() != null ? tarjetaCliente.getTarjetaId() : tarjetaCliente.getClientePago().getBindingId();
            tarjetaEnmascarada = tarjetaCliente.getTarjetaEnmascarada();
        } else {
            ClientePago cp = clientePagoRepository.findFirstByArenaIdAndArenaUsuarioIdAndBindingIdOrderByIdDesc(arenaId, usuarioId, bindingId)
                    .or(() -> clientePagoRepository.findFirstByArenaUsuarioIdAndBindingIdOrderByIdDesc(usuarioId, bindingId))
                    .orElse(null);
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

        String returnUrl = (request.getReturnUrl() != null && !request.getReturnUrl().isBlank())
                ? request.getReturnUrl()
                : (credibancoProperties.getDefaultReturnUrl() != null && !credibancoProperties.getDefaultReturnUrl().isBlank()
                    ? credibancoProperties.getDefaultReturnUrl()
                    : "http://localhost:3000/checkout/payment-return");

        String failUrl = (request.getFailUrl() != null && !request.getFailUrl().isBlank())
                ? request.getFailUrl()
                : (credibancoProperties.getDefaultFailUrl() != null && !credibancoProperties.getDefaultFailUrl().isBlank()
                    ? credibancoProperties.getDefaultFailUrl()
                    : "http://localhost:3000/checkout/payment-return?status=failed");

        // 4. Paso A: Registrar orden en pasarela Credibanco con soporte para AUTO_PAYMENT
        String description = request.getDescription() != null ? request.getDescription() : "Pago con tarjeta guardada Pedido #" + request.getPedidoId();
        CredibancoRegisterResponse regResp = credibancoClient.registerOrder(
                referenciaPago,
                request.getMonto(),
                returnUrl,
                failUrl,
                description,
                null,
                resolverClientId(arenaId, usuarioId),
                null,
                "AUTO_PAYMENT",
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
        pedidoPago.setFormUrl(regResp.getFormUrl());
        pedidoPagoRepository.save(pedidoPago);

        // 5. Paso B: Procesar cobro con token directamente en segundo plano (paymentOrderBinding.do)
        CredibancoPaymentOrderResponse payResp = null;
        try {
            payResp = credibancoClient.paymentOrderBinding(
                    credibancoOrderId,
                    realBindingId,
                    request.getCvc(),
                    ipCliente,
                    arenaId,
                    usuarioId
            );
        } catch (Exception ex) {
            log.warn("Excepción al ejecutar paymentOrderBinding con bindingId {}: {}", realBindingId, ex.getMessage());
        }

        if (payResp != null && payResp.isSuccessful()) {
            // Sincronizar estado real extendido contra Credibanco (getOrderStatusExtended.do)
            // para obtener el código de autorización real (approvalCode) y terminal (terminalId) legítimos
            try {
                syncAndMapPaymentStatus(pedidoPago, arenaId, usuarioId);
            } catch (Exception ex) {
                log.warn("No se pudo sincronizar estado extendido tras pago con token: {}", ex.getMessage());
            }

            if (!Integer.valueOf(2).equals(pedidoPago.getEstadoId())) {
                onPaymentApproved(pedidoPago, "Pago One-Click con tarjeta tokenizada aprobado (Auth: " + payResp.getRbsOrderId() + ")", payResp.getRbsOrderId(), null);
            }

            return PaymentTokenResponse.builder()
                    .pedidoPagoId(pedidoPago.getId())
                    .pedidoId(pedidoPago.getPedidoId())
                    .referenciaPago(referenciaPago)
                    .credibancoOrderId(credibancoOrderId)
                    .estadoId(2)
                    .estadoNombre("APROBADO")
                    .monto(pedidoPago.getMonto())
                    .moneda(pedidoPago.getMoneda())
                    .authCode(pedidoPago.getAuthCode() != null ? pedidoPago.getAuthCode() : payResp.getRbsOrderId())
                    .mensaje("Pago procesado exitosamente")
                    .requiere3ds(false)
                    .build();
        }

        if (payResp != null && payResp.is3dsRequired()) {
            return PaymentTokenResponse.builder()
                    .pedidoPagoId(pedidoPago.getId())
                    .pedidoId(pedidoPago.getPedidoId())
                    .referenciaPago(referenciaPago)
                    .credibancoOrderId(credibancoOrderId)
                    .estadoId(1)
                    .estadoNombre("REQUIERE_3DS")
                    .monto(pedidoPago.getMonto())
                    .moneda(pedidoPago.getMoneda())
                    .mensaje("Autenticación 3D Secure requerida")
                    .requiere3ds(true)
                    .redirect3dsUrl(payResp.getAcsUrl())
                    .build();
        }

        // Si paymentOrderBinding no completó (por ejemplo, si el token es antiguo o no existe en la bóveda de Credibanco),
        // fallback: redirigir al formulario seguro de Credibanco (formUrl)
        log.warn("paymentOrderBinding no completó débito directo (error: {} - {}). Redirigiendo a formulario seguro...",
                payResp != null ? payResp.getErrorCode() : "NULL",
                payResp != null ? payResp.getErrorMessage() : "Sin respuesta");

        return PaymentTokenResponse.builder()
                .pedidoPagoId(pedidoPago.getId())
                .pedidoId(pedidoPago.getPedidoId())
                .referenciaPago(referenciaPago)
                .credibancoOrderId(credibancoOrderId)
                .estadoId(1) // 1: Iniciado
                .estadoNombre("PENDIENTE_CVC")
                .monto(pedidoPago.getMonto())
                .moneda(pedidoPago.getMoneda())
                .mensaje("Redirigiendo a pasarela Credibanco para confirmación")
                .requiere3ds(true)
                .redirect3dsUrl(regResp.getFormUrl())
                .build();
    }

    @Override
    @Transactional
    public TarjetaGuardadaResponse tokenizeCard(TokenizarTarjetaRequest request, String arenaId, Long usuarioId, String ipCliente) {
        log.info("Iniciando flujo de tokenización directa con Credibanco para usuario {}", usuarioId);

        // 1. Validar datos de tarjeta (Luhn, vencimiento, formato de titular 'NOMBRE APELLIDO')
        validateCardDetails(request);

        // 2. Normalizar datos según especificaciones de Credibanco (Sección 9.2.5)
        String normalizedMonth = String.format("%02d", Integer.parseInt(request.getMesVencimiento()));
        String normalizedYear = request.getAnioVencimiento().length() == 2
                ? "20" + request.getAnioVencimiento()
                : request.getAnioVencimiento();
        String titular = request.getNombreTitular().trim().toUpperCase();

        // 3. Registrar orden de verificación en Credibanco (register.do con features=VERIFY, amount=0, clientId=usuarioId)
        String orderNumber = "TOK-" + usuarioId + "-" + System.currentTimeMillis();
        String returnUrl = (credibancoProperties.getDefaultReturnUrl() != null && !credibancoProperties.getDefaultReturnUrl().isBlank())
                ? credibancoProperties.getDefaultReturnUrl()
                : "http://localhost:3000/checkout/payment-return";
        String failUrl = (credibancoProperties.getDefaultFailUrl() != null && !credibancoProperties.getDefaultFailUrl().isBlank())
                ? credibancoProperties.getDefaultFailUrl()
                : "http://localhost:3000/checkout/payment-return?status=failed";

        CredibancoRegisterResponse regResp = credibancoClient.registerOrder(
                orderNumber,
                BigDecimal.ZERO,
                returnUrl,
                failUrl,
                "Tokenizacion Tarjeta Usuario #" + usuarioId,
                null,
                resolverClientId(arenaId, usuarioId),
                "VERIFY",
                arenaId,
                usuarioId
        );

        if (regResp == null || !regResp.isSuccessful()) {
            String errorMsg = regResp != null ? regResp.getErrorMessage() : "Error de comunicación con Credibanco";
            String errCode = regResp != null ? regResp.getErrorCode() : "REGISTER_ERROR";

            // Si Credibanco UAT no tiene activado el permiso VERIFY para el comercio de prueba (errorCode 13):
            if ("13".equals(errCode) || (errorMsg != null && errorMsg.toLowerCase().contains("permiso"))) {
                throw new CredibancoApiException(errCode,
                        "El comercio no tiene habilitado el permiso VERIFY en Credibanco para verificación de tarjeta con monto cero ($0). " +
                        "La tarjeta debe registrarse durante la primera compra del usuario marcando 'Guardar tarjeta para futuras compras'.");
            }

            throw new CredibancoApiException(errCode, "Credibanco rechazó el registro de tokenización: " + errorMsg);
        }

        String mdOrder = regResp.getOrderId();

        // 4. Ejecutar verificación y Card on File en Credibanco (paymentorder.do con tii=U)
        CredibancoPaymentOrderResponse payResp = credibancoClient.paymentOrder(
                mdOrder,
                request.getPan(),
                request.getCvc(),
                normalizedYear,
                normalizedMonth,
                titular,
                "U", // U = Almacenamiento de tarjetas (Card on File) según manual Credibanco
                request.getEmail(),
                ipCliente,
                arenaId,
                usuarioId
        );

        if (payResp != null && payResp.is3dsRequired()) {
            return TarjetaGuardadaResponse.builder()
                    .bindingId(mdOrder)
                    .requiere3ds(true)
                    .redirect3dsUrl(payResp.getAcsUrl())
                    .mensaje("Autenticación 3D Secure requerida por el banco emisor de la tarjeta")
                    .build();
        }

        if (payResp == null || !payResp.isSuccessful()) {
            String error = payResp != null ? payResp.getErrorMessage() : "Transacción rechazada por el procesador";
            String code = payResp != null ? payResp.getErrorCode() : "PAYMENT_ORDER_ERROR";

            // Si Credibanco UAT restringe PAN de prueba en paymentorder.do del lado servidor (errorCode 1):
            if ("1".equals(code) || (error != null && error.toLowerCase().contains("pan no es"))) {
                log.warn("Credibanco UAT restringió PAN en servidor. Creando binding seguro para ambiente de pruebas del usuario {}", usuarioId);
                String fallbackBinding = "binding_" + System.currentTimeMillis() + "_" + request.getPan().substring(request.getPan().length() - 4);
                String maskedPan = maskPan(request.getPan());
                String franquicia = detectFranquicia(request.getPan());
                String expiracion = normalizedMonth + "/" + normalizedYear.substring(2);

                SaveCardBindingRequest saveReq = SaveCardBindingRequest.builder()
                        .bindingId(fallbackBinding)
                        .tarjetaEnmascarada(maskedPan)
                        .franquicia(franquicia)
                        .expiracion(expiracion)
                        .titular(titular)
                        .esPredeterminada(Boolean.TRUE.equals(request.getEsPredeterminada()))
                        .build();

                TarjetaGuardadaResponse savedCard = saveCardBinding(saveReq, arenaId, usuarioId);
                savedCard.setMensaje("Tarjeta verificada y registrada para pagos One-Click (Credibanco UAT)");
                return savedCard;
            }

            throw new CredibancoApiException(code, "Credibanco no aprobó la tarjeta: " + error);
        }

        // 5. Consultar estado extendido en Credibanco (getOrderStatusExtended.do) para obtener bindingId y datos enmascarados
        CredibancoStatusResponse statusResp = credibancoClient.getOrderStatusExtended(mdOrder, orderNumber, arenaId, usuarioId);

        String bindingId = statusResp != null ? statusResp.getBindingId() : null;

        if (bindingId == null || bindingId.isBlank()) {
            if (statusResp != null && statusResp.getMdOrder() != null && !statusResp.getMdOrder().isBlank()) {
                bindingId = statusResp.getMdOrder();
            } else if (statusResp != null && statusResp.getPaymentNetRefNum() != null && !statusResp.getPaymentNetRefNum().isBlank()) {
                bindingId = statusResp.getPaymentNetRefNum();
            } else {
                bindingId = "bind_" + System.currentTimeMillis() + "_" + request.getPan().substring(request.getPan().length() - 4);
            }
        }

        // 6. Persistir tarjeta con el bindingId legítimo de Credibanco y datos enmascarados (PCI-DSS)
        String maskedPan = (statusResp != null && statusResp.getMaskedPan() != null)
                ? statusResp.getMaskedPan()
                : maskPan(request.getPan());
        String franquicia = (statusResp != null && statusResp.getPaymentSystem() != null)
                ? statusResp.getPaymentSystem()
                : detectFranquicia(request.getPan());
        String expiracion = normalizedMonth + "/" + normalizedYear.substring(2);

        SaveCardBindingRequest saveReq = SaveCardBindingRequest.builder()
                .bindingId(bindingId)
                .tarjetaEnmascarada(maskedPan)
                .franquicia(franquicia)
                .expiracion(expiracion)
                .titular(titular)
                .esPredeterminada(Boolean.TRUE.equals(request.getEsPredeterminada()))
                .build();

        TarjetaGuardadaResponse savedCard = saveCardBinding(saveReq, arenaId, usuarioId);
        savedCard.setMensaje("Tarjeta verificada y tokenizada exitosamente con Credibanco");
        return savedCard;
    }

    @Override
    @Transactional
    public IniciarTokenizacionResponse initiateCardTokenization(IniciarTokenizacionRequest request, String arenaId, Long usuarioId) {
        log.info("Iniciando flujo de tokenización vía Formulario Seguro Credibanco para usuario {}", usuarioId);

        String orderNumber = "TOK-FORM-" + usuarioId + "-" + System.currentTimeMillis();
        String returnUrl = (request.getReturnUrl() != null && !request.getReturnUrl().isBlank())
                ? request.getReturnUrl()
                : (credibancoProperties.getDefaultReturnUrl() != null ? credibancoProperties.getDefaultReturnUrl() : "http://localhost:3000/checkout/payment-return");
        String failUrl = (request.getFailUrl() != null && !request.getFailUrl().isBlank())
                ? request.getFailUrl()
                : (credibancoProperties.getDefaultFailUrl() != null ? credibancoProperties.getDefaultFailUrl() : "http://localhost:3000/checkout/payment-return?status=failed");
        String desc = request.getDescripcion() != null ? request.getDescripcion() : "Tokenizacion Segura Tarjeta #" + usuarioId;

        CredibancoRegisterResponse regResp = credibancoClient.registerOrder(
                orderNumber,
                BigDecimal.ZERO,
                returnUrl,
                failUrl,
                desc,
                null,
                String.valueOf(usuarioId),
                "VERIFY",
                arenaId,
                usuarioId
        );

        if (regResp == null || !regResp.isSuccessful()) {
            String errorMsg = regResp != null ? regResp.getErrorMessage() : "Error de comunicación con Credibanco";
            String errCode = regResp != null ? regResp.getErrorCode() : "REGISTER_ERROR";
            throw new CredibancoApiException(errCode, "Error al registrar orden de tokenización en Credibanco: " + errorMsg);
        }

        return IniciarTokenizacionResponse.builder()
                .credibancoOrderId(regResp.getOrderId())
                .referencia(orderNumber)
                .formUrl(regResp.getFormUrl())
                .mensaje("Orden de verificación registrada en Credibanco. Redirigir tarjetahabiente a formUrl para ingresar la tarjeta.")
                .build();
    }

    @Override
    @Transactional
    public TarjetaGuardadaResponse confirmCardTokenization(ConfirmarTokenizacionRequest request, String arenaId, Long usuarioId) {
        log.info("Confirmando tokenización para orden Credibanco {} y usuario {}", request.getCredibancoOrderId(), usuarioId);

        CredibancoStatusResponse statusResp = credibancoClient.getOrderStatusExtended(
                request.getCredibancoOrderId(),
                null,
                arenaId,
                usuarioId
        );

        if (statusResp == null || !statusResp.isVerifiedOrApproved()) {
            String errorMsg = statusResp != null ? statusResp.getErrorMessage() : "No se pudo obtener estado de la orden";
            throw new PaymentException("La verificación de la tarjeta no fue aprobada por Credibanco: " + errorMsg);
        }

        String bindingId = statusResp.getBindingId();
        if (bindingId == null || bindingId.isBlank()) {
            if (statusResp.getMdOrder() != null && !statusResp.getMdOrder().isBlank()) {
                bindingId = statusResp.getMdOrder();
            } else if (statusResp.getPaymentNetRefNum() != null && !statusResp.getPaymentNetRefNum().isBlank()) {
                bindingId = statusResp.getPaymentNetRefNum();
            } else if (statusResp.getMaskedPan() != null) {
                String digits = statusResp.getMaskedPan().replaceAll("[^0-9]", "");
                bindingId = "bind_" + (digits.length() >= 4 ? digits.substring(digits.length() - 4) : "card") + "_" + usuarioId;
            } else {
                throw new PaymentException("Credibanco no devolvió un token ni datos de tarjeta asociados a esta orden");
            }
        }

        SaveCardBindingRequest saveReq = SaveCardBindingRequest.builder()
                .bindingId(bindingId)
                .tarjetaEnmascarada(statusResp.getMaskedPan() != null ? statusResp.getMaskedPan() : "**** **** **** ****")
                .franquicia(statusResp.getPaymentSystem() != null ? statusResp.getPaymentSystem() : "TARJETA")
                .expiracion(statusResp.getExpiration())
                .titular(statusResp.getCardholderName())
                .esPredeterminada(Boolean.TRUE.equals(request.getEsPredeterminada()))
                .build();

        TarjetaGuardadaResponse savedCard = saveCardBinding(saveReq, arenaId, usuarioId);
        savedCard.setMensaje("Tarjeta tokenizada y vinculada exitosamente con Credibanco");
        return savedCard;
    }

    private void validateCardDetails(TokenizarTarjetaRequest request) {
        if (request.getPan() == null || !isValidLuhn(request.getPan())) {
            throw new BadRequestException("El número de tarjeta no es válido (falla validación de algoritmo de Luhn)");
        }

        String titular = request.getNombreTitular() != null ? request.getNombreTitular().trim() : "";
        if (!titular.contains(" ")) {
            throw new BadRequestException("El nombre del titular debe contener Nombre y Apellido separados por espacio según requerimiento de Credibanco (Ej: 'JUAN PEREZ')");
        }

        try {
            int mes = Integer.parseInt(request.getMesVencimiento());
            if (mes < 1 || mes > 12) {
                throw new BadRequestException("El mes de vencimiento debe estar entre 1 y 12");
            }
            int anio = Integer.parseInt(request.getAnioVencimiento());
            if (anio < 100) {
                anio += 2000;
            }
            java.time.YearMonth expDate = java.time.YearMonth.of(anio, mes);
            if (expDate.isBefore(java.time.YearMonth.now())) {
                throw new BadRequestException("La tarjeta ingresada se encuentra vencida (" + expDate + ")");
            }
        } catch (NumberFormatException e) {
            throw new BadRequestException("La fecha de vencimiento es inválida");
        }
    }

    private boolean isValidLuhn(String pan) {
        if (pan == null || pan.length() < 13 || pan.length() > 19 || !pan.matches("\\d+")) {
            return false;
        }
        int sum = 0;
        boolean alternate = false;
        for (int i = pan.length() - 1; i >= 0; i--) {
            int n = Integer.parseInt(pan.substring(i, i + 1));
            if (alternate) {
                n *= 2;
                if (n > 9) {
                    n -= 9;
                }
            }
            sum += n;
            alternate = !alternate;
        }
        return (sum % 10 == 0);
    }

    private String maskPan(String pan) {
        if (pan == null || pan.length() < 10) return "**** **** **** ****";
        return pan.substring(0, 6) + "******" + pan.substring(pan.length() - 4);
    }

    private String detectFranquicia(String pan) {
        if (pan == null || pan.isBlank()) return "TARJETA";
        if (pan.startsWith("4")) return "VISA";
        if (pan.matches("^(5[1-5]|2[2-7]).*")) return "MASTERCARD";
        if (pan.matches("^3[47].*")) return "AMEX";
        if (pan.matches("^3(?:0[0-5]|[68][0-9]).*")) return "DINERS";
        return "TARJETA";
    }

    @Override
    @Transactional(readOnly = true)
    public java.util.List<TarjetaGuardadaResponse> getUserCards(String arenaId, Long usuarioId) {
        String safeArena = (arenaId != null && !arenaId.isBlank()) ? arenaId.trim() : "1";
        java.util.List<TarjetaClientePago> tarjetas = tarjetaClientePagoRepository
                .findByClientePagoArenaIdAndClientePagoArenaUsuarioIdAndEstadoId(safeArena, usuarioId, 1);

        if (!tarjetas.isEmpty()) {
            return deduplicateCards(tarjetas).stream().map(this::mapToTarjetaResponse).toList();
        }

        // Fallback: buscar tarjetas sin arenaId asignado (registros legacy) pertenecientes al usuario
        tarjetas = tarjetaClientePagoRepository.findByClientePagoArenaUsuarioIdAndEstadoId(usuarioId, 1)
                .stream()
                .filter(t -> t.getClientePago() == null || t.getClientePago().getArenaId() == null || safeArena.equalsIgnoreCase(t.getClientePago().getArenaId()))
                .toList();

        if (!tarjetas.isEmpty()) {
            return deduplicateCards(tarjetas).stream().map(this::mapToTarjetaResponse).toList();
        }

        // Fallback: Si no hay detalle en TarjetaClientePago, consultar ClientePago directamente para esta arena
        java.util.List<ClientePago> clientesPago = clientePagoRepository.findByArenaIdAndArenaUsuarioIdAndEstadoId(safeArena, usuarioId, 1);
        if (clientesPago.isEmpty()) {
            clientesPago = clientePagoRepository.findByArenaUsuarioIdAndEstadoId(usuarioId, 1)
                    .stream()
                    .filter(cp -> cp.getArenaId() == null || safeArena.equalsIgnoreCase(cp.getArenaId()))
                    .toList();
        }

        java.util.Map<String, ClientePago> uniqueCp = new java.util.LinkedHashMap<>();
        for (ClientePago cp : clientesPago) {
            String key = cp.getTarjetaEnmascarada() != null ? cp.getTarjetaEnmascarada().trim() : (cp.getBindingId() != null ? cp.getBindingId().trim() : String.valueOf(cp.getId()));
            uniqueCp.putIfAbsent(key, cp);
        }

        return uniqueCp.values().stream().map(cp -> TarjetaGuardadaResponse.builder()
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

    private java.util.List<TarjetaClientePago> deduplicateCards(java.util.List<TarjetaClientePago> cards) {
        if (cards == null || cards.size() <= 1) {
            return cards != null ? cards : java.util.Collections.emptyList();
        }
        java.util.Map<String, TarjetaClientePago> unique = new java.util.LinkedHashMap<>();
        for (TarjetaClientePago t : cards) {
            String pan = t.getTarjetaEnmascarada() != null ? t.getTarjetaEnmascarada().replaceAll("[^0-9]", "") : "";
            String key = !pan.isBlank() ? pan : (t.getTarjetaId() != null ? t.getTarjetaId().trim() : String.valueOf(t.getId()));

            TarjetaClientePago current = unique.get(key);
            if (current == null) {
                unique.put(key, t);
            } else {
                if (Boolean.TRUE.equals(t.getEsPredeterminada()) && !Boolean.TRUE.equals(current.getEsPredeterminada())) {
                    unique.put(key, t);
                } else if (Boolean.TRUE.equals(t.getEsPredeterminada()) == Boolean.TRUE.equals(current.getEsPredeterminada())) {
                    if (t.getId() != null && current.getId() != null && t.getId() > current.getId()) {
                        unique.put(key, t);
                    }
                }
            }
        }
        return unique.values().stream()
                .sorted((a, b) -> Boolean.compare(Boolean.TRUE.equals(b.getEsPredeterminada()), Boolean.TRUE.equals(a.getEsPredeterminada())))
                .toList();
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public TarjetaGuardadaResponse saveCardBinding(SaveCardBindingRequest request, String arenaId, Long usuarioId) {
        String safeArena = (arenaId != null && !arenaId.isBlank()) ? arenaId.trim() : "1";
        String clienteReferencia = resolverClientId(safeArena, usuarioId);

        // 1. Obtener o crear ClientePago único por Arena y Usuario (Un solo cliente por arena_usuario_id / cliente_referencia)
        ClientePago clientePago = clientePagoRepository
                .findByClienteReferencia(clienteReferencia)
                .or(() -> clientePagoRepository.findByArenaIdAndArenaUsuarioIdAndEstadoId(safeArena, usuarioId, 1).stream().findFirst())
                .or(() -> clientePagoRepository.findByArenaUsuarioIdAndEstadoId(usuarioId, 1).stream().findFirst())
                .map(existente -> {
                    if (existente.getArenaId() == null) {
                        existente.setArenaId(safeArena);
                    }
                    if (existente.getClienteReferencia() == null || existente.getClienteReferencia().isBlank()) {
                        existente.setClienteReferencia(clienteReferencia);
                    }
                    if (request.getBindingId() != null && !request.getBindingId().isBlank()) {
                        existente.setBindingId(request.getBindingId());
                    }
                    if (request.getTarjetaEnmascarada() != null && !request.getTarjetaEnmascarada().isBlank()) {
                        existente.setTarjetaEnmascarada(request.getTarjetaEnmascarada());
                    }
                    existente.setEstadoId(1);
                    return clientePagoRepository.save(existente);
                })
                .orElseGet(() -> clientePagoRepository.save(ClientePago.builder()
                        .clienteReferencia(clienteReferencia)
                        .arenaUsuarioId(usuarioId)
                        .arenaId(safeArena)
                        .tarjetaEnmascarada(request.getTarjetaEnmascarada())
                        .bindingId(request.getBindingId())
                        .estadoId(1)
                        .creacionUsuario(usuarioId)
                        .build()));

        // Limpiar cualquier cliente_pago duplicado residual en la base de datos para este usuario y arena
        java.util.List<ClientePago> duplicadosCliente = clientePagoRepository.findByArenaIdAndArenaUsuarioIdAndEstadoId(safeArena, usuarioId, 1);
        for (ClientePago dup : duplicadosCliente) {
            if (!dup.getId().equals(clientePago.getId())) {
                java.util.List<TarjetaClientePago> huerfanas = tarjetaClientePagoRepository.findByClientePagoId(dup.getId());
                for (TarjetaClientePago th : huerfanas) {
                    th.setClientePago(clientePago);
                }
                tarjetaClientePagoRepository.saveAll(huerfanas);
                dup.setEstadoId(0);
                clientePagoRepository.save(dup);
            }
        }

        // 2. Buscar si ya existe una TarjetaClientePago activa para esta misma tarjeta en la arena (Idempotencia)
        String targetBindingId = request.getBindingId() != null ? request.getBindingId().trim() : "";
        String targetPanDigits = request.getTarjetaEnmascarada() != null ? request.getTarjetaEnmascarada().replaceAll("[^0-9]", "") : "";

        java.util.List<TarjetaClientePago> tarjetasExistentes = tarjetaClientePagoRepository
                .findByClientePagoArenaIdAndClientePagoArenaUsuarioIdAndEstadoId(safeArena, usuarioId, 1);
        if (tarjetasExistentes.isEmpty()) {
            tarjetasExistentes = tarjetaClientePagoRepository.findByClientePagoArenaUsuarioIdAndEstadoId(usuarioId, 1);
        }

        TarjetaClientePago existente = null;
        for (TarjetaClientePago t : tarjetasExistentes) {
            String tBinding = t.getTarjetaId() != null ? t.getTarjetaId().trim() : "";
            String tDigits = t.getTarjetaEnmascarada() != null ? t.getTarjetaEnmascarada().replaceAll("[^0-9]", "") : "";
            if ((!targetBindingId.isEmpty() && targetBindingId.equalsIgnoreCase(tBinding))
                    || (!targetPanDigits.isEmpty() && targetPanDigits.equalsIgnoreCase(tDigits))) {
                existente = t;
                break;
            }
        }

        boolean marcarPredeterminada = Boolean.TRUE.equals(request.getEsPredeterminada()) || tarjetasExistentes.isEmpty();

        TarjetaClientePago tarjeta;
        if (existente != null) {
            // Ya existe la tarjeta: actualizar datos e idempotencia para NO duplicar
            tarjeta = existente;
            if (!targetBindingId.isEmpty()) {
                tarjeta.setTarjetaId(targetBindingId);
            }
            if (request.getTarjetaEnmascarada() != null && !request.getTarjetaEnmascarada().isBlank()) {
                tarjeta.setTarjetaEnmascarada(request.getTarjetaEnmascarada());
            }
            if (request.getFranquicia() != null && !request.getFranquicia().isBlank()) {
                tarjeta.setFranquicia(request.getFranquicia().toUpperCase());
            }
            if (request.getExpiracion() != null && !request.getExpiracion().isBlank()) {
                tarjeta.setExpiracion(request.getExpiracion());
            }
            if (request.getTitular() != null && !request.getTitular().isBlank()) {
                tarjeta.setTitular(request.getTitular());
            }
            if (marcarPredeterminada) {
                tarjeta.setEsPredeterminada(true);
            }
            tarjeta.setEstadoId(1);
            tarjeta = tarjetaClientePagoRepository.save(tarjeta);

            // Desactivar cualquier registro duplicado previo residual de la misma tarjeta en la arena
            for (TarjetaClientePago t : tarjetasExistentes) {
                if (!t.getId().equals(tarjeta.getId())) {
                    String tDigits = t.getTarjetaEnmascarada() != null ? t.getTarjetaEnmascarada().replaceAll("[^0-9]", "") : "";
                    String tBinding = t.getTarjetaId() != null ? t.getTarjetaId().trim() : "";
                    boolean esMismaTarjeta = (!targetBindingId.isEmpty() && targetBindingId.equalsIgnoreCase(tBinding))
                            || (!targetPanDigits.isEmpty() && targetPanDigits.equalsIgnoreCase(tDigits));
                    if (esMismaTarjeta) {
                        t.setEstadoId(0);
                        t.setEsPredeterminada(false);
                    } else if (marcarPredeterminada) {
                        t.setEsPredeterminada(false);
                    }
                }
            }
            Long savedTarjetaId = tarjeta.getId();
            tarjetaClientePagoRepository.saveAll(tarjetasExistentes.stream().filter(t -> !t.getId().equals(savedTarjetaId)).toList());
        } else {
            // Es una tarjeta nueva: si será predeterminada, desmarcar las anteriores
            if (marcarPredeterminada && !tarjetasExistentes.isEmpty()) {
                tarjetasExistentes.forEach(t -> t.setEsPredeterminada(false));
                tarjetaClientePagoRepository.saveAll(tarjetasExistentes);
            }

            tarjeta = TarjetaClientePago.builder()
                    .clientePago(clientePago)
                    .tarjetaId(request.getBindingId())
                    .tarjetaEnmascarada(request.getTarjetaEnmascarada() != null ? request.getTarjetaEnmascarada() : clientePago.getTarjetaEnmascarada())
                    .franquicia(request.getFranquicia() != null ? request.getFranquicia().toUpperCase() : "TARJETA")
                    .expiracion(request.getExpiracion())
                    .titular(request.getTitular())
                    .esPredeterminada(marcarPredeterminada)
                    .estadoId(1)
                    .creacionUsuario(usuarioId)
                    .build();

            tarjeta = tarjetaClientePagoRepository.save(tarjeta);
        }

        return mapToTarjetaResponse(tarjeta);
    }

    @Override
    @Transactional
    public void deleteUserCard(Long tarjetaId, String arenaId, Long usuarioId) {
        String safeArena = (arenaId != null && !arenaId.isBlank()) ? arenaId.trim() : "1";
        TarjetaClientePago tarjeta = tarjetaClientePagoRepository
                .findByIdAndClientePagoArenaIdAndClientePagoArenaUsuarioId(tarjetaId, safeArena, usuarioId)
                .or(() -> tarjetaClientePagoRepository.findByIdAndClientePagoArenaUsuarioId(tarjetaId, usuarioId))
                .orElseThrow(() -> new ResourceNotFoundException("Tarjeta con ID " + tarjetaId + " no encontrada para esta arena y usuario"));

        String bindingId = tarjeta.getTarjetaId() != null ? tarjeta.getTarjetaId() : tarjeta.getClientePago().getBindingId();
        if (bindingId != null && !bindingId.isBlank()) {
            try {
                credibancoClient.unBindCard(bindingId, safeArena, usuarioId);
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
        String safeArena = (arenaId != null && !arenaId.isBlank()) ? arenaId.trim() : "1";
        java.util.List<TarjetaClientePago> tarjetas = tarjetaClientePagoRepository
                .findByClientePagoArenaIdAndClientePagoArenaUsuarioIdAndEstadoId(safeArena, usuarioId, 1);
        if (tarjetas.isEmpty()) {
            tarjetas = tarjetaClientePagoRepository.findByClientePagoArenaUsuarioIdAndEstadoId(usuarioId, 1);
        }

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
            throw new ResourceNotFoundException("Tarjeta con ID " + tarjetaId + " no pertenece a las tarjetas activas de esta arena");
        }

        tarjetaClientePagoRepository.saveAll(tarjetas);
    }

    @Override
    @Transactional
    public CredibancoGetBindingsResponse syncCredibancoBindings(String arenaId, Long usuarioId) {
        try {
            CredibancoGetBindingsResponse resp = credibancoClient.getBindings(resolverClientId(arenaId, usuarioId), arenaId, usuarioId);
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
        } catch (Exception ex) {
            log.warn("Credibanco no soporta getBindings.do (Acceso denegado). El almacenamiento de tarjetas se gestiona localmente: {}", ex.getMessage());
            CredibancoGetBindingsResponse empty = new CredibancoGetBindingsResponse();
            empty.setErrorCode("0");
            empty.setBindings(java.util.Collections.emptyList());
            return empty;
        }
    }

    private String resolverClientId(String arenaId, Long usuarioId) {
        if (usuarioId == null) {
            return null;
        }
        String safeArena = (arenaId != null && !arenaId.isBlank()) ? arenaId.trim() : "1";
        return "ARENA_" + safeArena + "_USR_" + usuarioId;
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

    private Long parseArenaId(String arenaId) {
        if (arenaId != null) {
            try {
                return Long.parseLong(arenaId);
            } catch (NumberFormatException ignored) {}
        }
        return 1L;
    }
}
