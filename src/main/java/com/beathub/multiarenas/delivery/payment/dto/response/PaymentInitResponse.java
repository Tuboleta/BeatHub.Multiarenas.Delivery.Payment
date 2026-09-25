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
public class PaymentInitResponse {
    private Long pedidoPagoId;
    private Long pedidoId;
    private String referenciaPago;
    private String credibancoOrderId;
    private String formUrl;
    private BigDecimal monto;
    private String moneda;
    private Integer estadoId;
    private String estadoDescripcion;
}
