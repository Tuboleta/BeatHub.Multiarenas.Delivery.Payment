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
    private String credibancoOrderId;
    private BigDecimal montoReembolsado;
    private boolean exito;
    private String mensaje;
}
