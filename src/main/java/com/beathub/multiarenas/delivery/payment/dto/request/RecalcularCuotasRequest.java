package com.beathub.multiarenas.delivery.payment.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecalcularCuotasRequest {

    @Builder.Default
    private String divisionTipo = "EQUITATIVA";

    private Integer cantidadPersonas;
}
