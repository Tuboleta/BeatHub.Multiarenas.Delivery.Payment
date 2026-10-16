package com.beathub.multiarenas.delivery.payment.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ConfirmarTokenizacionRequest {

    @NotBlank(message = "El credibancoOrderId es obligatorio")
    private String credibancoOrderId;

    @Builder.Default
    private Boolean esPredeterminada = false;
}
