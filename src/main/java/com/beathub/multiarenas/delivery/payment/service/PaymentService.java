package com.beathub.multiarenas.delivery.payment.service;

import com.beathub.multiarenas.delivery.payment.dto.request.InitPaymentRequest;
import com.beathub.multiarenas.delivery.payment.dto.request.RefundPaymentRequest;
import com.beathub.multiarenas.delivery.payment.dto.request.VerifyCardRequest;
import com.beathub.multiarenas.delivery.payment.dto.response.*;

public interface PaymentService {
    PaymentInitResponse initiatePayment(InitPaymentRequest request, String arenaId, Long usuarioId);
    PaymentStatusResponse queryPaymentStatus(Long pedidoPagoId, String arenaId, Long usuarioId);
    PaymentStatusResponse queryPaymentStatusByReference(String referenciaPago, String arenaId, Long usuarioId);
    void processCredibancoCallback(String mdOrder, String orderNumber, String operation, Integer status, String checksum, String signAlias);
    RefundResponse refundPayment(RefundPaymentRequest request, String arenaId, Long usuarioId);
    VerifyCardResponse verifyCard(VerifyCardRequest request, String arenaId, Long usuarioId);
}
