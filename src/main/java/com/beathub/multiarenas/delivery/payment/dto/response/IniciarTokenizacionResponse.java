package com.beathub.multiarenas.delivery.payment.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IniciarTokenizacionResponse {
    private String credibancoOrderId;
    private String referencia;
    private String formUrl;
    private String mensaje;
}
