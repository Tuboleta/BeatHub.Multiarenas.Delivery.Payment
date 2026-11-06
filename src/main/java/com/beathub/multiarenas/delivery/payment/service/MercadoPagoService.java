package com.beathub.multiarenas.delivery.payment.service;

import com.beathub.multiarenas.delivery.payment.dto.request.*;
import com.beathub.multiarenas.delivery.payment.dto.response.*;

import java.util.List;

public interface MercadoPagoService {

    /**
     * Iniciar orden con la API v1/orders de Mercado Pago
     */
    PaymentInitResponse initiateOrder(InitPaymentRequest request, String arenaId, Long usuarioId);

    /**
     * Consultar y sincronizar estado de orden por pedidoPagoId
     */
    PaymentStatusResponse queryOrderStatus(Long pedidoPagoId, String arenaId, Long usuarioId);

    /**
     * Consultar y sincronizar estado por referencia de pago (PAY-...)
     */
    PaymentStatusResponse queryOrderByReference(String referenciaPago, String arenaId, Long usuarioId);

    /**
     * Consultar y sincronizar estado por Order ID de Mercado Pago
     */
    PaymentStatusResponse queryOrderByMpOrderId(String mpOrderId, String arenaId, Long usuarioId);

    /**
     * Consultar y sincronizar estado por ID de Pedido
     */
    PaymentStatusResponse queryOrderByPedidoId(Long pedidoId, String arenaId, Long usuarioId);

    /**
     * Pagar con tarjeta guardada o perfil de pago recurrente (1-Click) via v1/orders
     */
    PaymentTokenResponse payWithToken(PayWithTokenRequest request, String arenaId, Long usuarioId, String ipCliente);

    /**
     * Reembolsar orden ante Mercado Pago (v1/orders/{orderId}/refund)
     */
    RefundResponse refundOrder(RefundPaymentRequest request, String arenaId, Long usuarioId);

    /**
     * Tokenizar y guardar tarjeta del usuario para pagos recurrentes en Mercado Pago
     */
    TarjetaGuardadaResponse tokenizeAndSaveCard(TokenizarTarjetaRequest request, String arenaId, Long usuarioId, String ipCliente);

    /**
     * Obtener tarjetas activas asociadas al cliente en Mercado Pago
     */
    List<TarjetaGuardadaResponse> getUserCards(String arenaId, Long usuarioId);

    /**
     * Eliminar tarjeta vinculada
     */
    void deleteUserCard(Long tarjetaId, String arenaId, Long usuarioId);

    /**
     * Procesar webhook de notificación de Mercado Pago con validación HMAC SHA256
     */
    void processWebhook(String rawBody, String xSignature, String xRequestId, String dataId);
}

