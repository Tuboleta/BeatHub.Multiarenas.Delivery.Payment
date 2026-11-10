package com.beathub.multiarenas.delivery.payment.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentStatusResponse {
    private Long pedidoPagoId;
    private Long pedidoId;
    private String referenciaPago;
    private String orderId;
    private String mdOrder;
    private BigDecimal monto;
    private String moneda;
    private Integer estadoId;
    private String estadoNombre;
    private String actionCode;
    private String actionCodeDescription;
    private String authCode;
    private String autorizacion;
    private String terminal;
    private String medioPago;
    private String errorCode;
    private String errorMessage;
    private LocalDateTime fechaPago;

    public void setCredibancoOrderId(String credibancoOrderId) {
        this.orderId = credibancoOrderId;
    }

    public String getAutorizacion() {
        if (autorizacion != null && !autorizacion.isBlank()) {
            return autorizacion;
        }
        return authCode;
    }

    public static class PaymentStatusResponseBuilder {
        public PaymentStatusResponseBuilder credibancoOrderId(String credibancoOrderId) {
            this.orderId = credibancoOrderId;
            return this;
        }

        public PaymentStatusResponseBuilder autorizacion(String autorizacion) {
            this.autorizacion = autorizacion;
            if (this.authCode == null || this.authCode.isBlank()) {
                this.authCode = autorizacion;
            }
            return this;
        }
    }
}
