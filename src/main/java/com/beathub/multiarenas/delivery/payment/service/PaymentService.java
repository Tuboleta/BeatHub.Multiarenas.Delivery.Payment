package com.beathub.multiarenas.delivery.payment.service;

import com.beathub.multiarenas.delivery.payment.client.dto.CredibancoGetBindingsResponse;
import com.beathub.multiarenas.delivery.payment.dto.request.InitPaymentRequest;
import com.beathub.multiarenas.delivery.payment.dto.request.PayWithTokenRequest;
import com.beathub.multiarenas.delivery.payment.dto.request.RefundPaymentRequest;
import com.beathub.multiarenas.delivery.payment.dto.request.SaveCardBindingRequest;
import com.beathub.multiarenas.delivery.payment.dto.request.VerifyCardRequest;
import com.beathub.multiarenas.delivery.payment.dto.response.*;

import java.util.List;

public interface PaymentService {
    PaymentInitResponse initiatePayment(InitPaymentRequest request, String arenaId, Long usuarioId);
    PaymentStatusResponse queryPaymentStatus(Long pedidoPagoId, String arenaId, Long usuarioId);
    PaymentStatusResponse queryPaymentStatusByReference(String referenciaPago, String arenaId, Long usuarioId);
    PaymentStatusResponse queryPaymentStatusByPedidoId(Long pedidoId, String arenaId, Long usuarioId);
    PaymentStatusResponse queryPaymentStatusByCredibancoOrderId(String credibancoOrderId, String arenaId, Long usuarioId);
    void processCredibancoCallback(String mdOrder, String orderNumber, String operation, Integer status, String checksum, String signAlias);
    RefundResponse refundPayment(RefundPaymentRequest request, String arenaId, Long usuarioId);
    VerifyCardResponse verifyCard(VerifyCardRequest request, String arenaId, Long usuarioId);

    // Métodos para Tokenización y Tarjetas Guardadas (One-Click) con Credibanco
    TarjetaGuardadaResponse tokenizeCard(com.beathub.multiarenas.delivery.payment.dto.request.TokenizarTarjetaRequest request, String arenaId, Long usuarioId, String ipCliente);
    IniciarTokenizacionResponse initiateCardTokenization(com.beathub.multiarenas.delivery.payment.dto.request.IniciarTokenizacionRequest request, String arenaId, Long usuarioId);
    TarjetaGuardadaResponse confirmCardTokenization(com.beathub.multiarenas.delivery.payment.dto.request.ConfirmarTokenizacionRequest request, String arenaId, Long usuarioId);
    PaymentTokenResponse payWithToken(PayWithTokenRequest request, String arenaId, Long usuarioId, String ipCliente);
    List<TarjetaGuardadaResponse> getUserCards(String arenaId, Long usuarioId);
    TarjetaGuardadaResponse saveCardBinding(SaveCardBindingRequest request, String arenaId, Long usuarioId);
    void deleteUserCard(Long tarjetaId, String arenaId, Long usuarioId);
    void setDefaultCard(Long tarjetaId, String arenaId, Long usuarioId);
    CredibancoGetBindingsResponse syncCredibancoBindings(String arenaId, Long usuarioId);
}
