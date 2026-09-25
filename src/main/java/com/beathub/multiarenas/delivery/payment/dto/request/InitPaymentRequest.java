package com.beathub.multiarenas.delivery.payment.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Map;

@Data
public class InitPaymentRequest {

    @NotNull(message = "El pedidoId es obligatorio")
    private Long pedidoId;

    @NotNull(message = "El monto es obligatorio")
    @DecimalMin(value = "0.01", message = "El monto debe ser mayor a 0")
    private BigDecimal monto;

    private Integer tipoPagoId = 1; // 1: CREDIBANCO_GATEWAY
    private Integer medioPagoId;
    private Long grupoPagoUsuarioId;

    private String returnUrl;
    private String failUrl;
    private String description;

    // Datos antifraude / jsonParams
    private Map<String, Object> extraParams;
}
