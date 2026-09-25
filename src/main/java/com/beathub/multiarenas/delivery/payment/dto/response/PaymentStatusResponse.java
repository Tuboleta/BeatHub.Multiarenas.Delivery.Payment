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
    private String credibancoOrderId;
    private String mdOrder;
    private BigDecimal monto;
    private String moneda;
    private Integer estadoId;
    private String estadoNombre;
    private String actionCode;
    private String actionCodeDescription;
    private String authCode;
    private String errorCode;
    private String errorMessage;
    private LocalDateTime fechaPago;
}
