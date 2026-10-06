package com.beathub.multiarenas.delivery.payment.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PayWithTokenRequest {

    @NotNull(message = "El pedidoId es obligatorio")
    private Long pedidoId;

    @NotNull(message = "El monto es obligatorio")
    @Positive(message = "El monto debe ser mayor a cero")
    private BigDecimal monto;

    @NotBlank(message = "El bindingId o token de tarjeta es obligatorio")
    private String bindingId;

    /**
     * CVC / CVV opcional o requerido si la política del banco emisor o 3DS lo solicita
     */
    private String cvc;

    @Builder.Default
    private Integer tipoPagoId = 1; // 1: Único, 2: Grupal (Vaca)

    private Long grupoPagoUsuarioId;

    private String returnUrl;
    private String failUrl;
    private String description;
}
