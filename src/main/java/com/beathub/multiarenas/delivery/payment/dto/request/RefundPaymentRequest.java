package com.beathub.multiarenas.delivery.payment.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class RefundPaymentRequest {

    @NotNull(message = "El ID del pedido de pago es obligatorio")
    private Long pedidoPagoId;

    private BigDecimal monto;
    private String motivo;
}
