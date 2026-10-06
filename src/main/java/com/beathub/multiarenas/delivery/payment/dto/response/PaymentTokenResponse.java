package com.beathub.multiarenas.delivery.payment.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentTokenResponse {
    private Long pedidoPagoId;
    private Long pedidoId;
    private String referenciaPago;
    private String credibancoOrderId;
    private Integer estadoId;
    private String estadoNombre;
    private BigDecimal monto;
    private String moneda;
    private String authCode;
    private String actionCode;
    private String mensaje;
    private boolean requiere3ds;
    private String redirect3dsUrl;
    private LocalDateTime fechaPago;
}
