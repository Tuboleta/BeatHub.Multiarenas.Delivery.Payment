package com.beathub.multiarenas.delivery.payment.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RefundResponse {
    private Long pedidoPagoId;
    private String orderId;
    private BigDecimal montoReembolsado;
    private boolean exito;
    private String mensaje;

    public void setCredibancoOrderId(String credibancoOrderId) {
        this.orderId = credibancoOrderId;
    }

    public static class RefundResponseBuilder {
        public RefundResponseBuilder credibancoOrderId(String credibancoOrderId) {
            this.orderId = credibancoOrderId;
            return this;
        }
    }
}
