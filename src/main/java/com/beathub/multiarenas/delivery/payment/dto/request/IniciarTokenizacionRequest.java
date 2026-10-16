package com.beathub.multiarenas.delivery.payment.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IniciarTokenizacionRequest {
    private String returnUrl;
    private String failUrl;
    private String descripcion;

    @Builder.Default
    private Boolean esPredeterminada = false;
}
