package com.beathub.multiarenas.delivery.payment.dto.request;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ActualizarTarjetaAliasRequest {

    @Size(max = 100, message = "El alias no puede superar los 100 caracteres")
    private String alias;
}
