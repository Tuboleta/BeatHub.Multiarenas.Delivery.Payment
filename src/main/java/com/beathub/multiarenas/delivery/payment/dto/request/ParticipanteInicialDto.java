package com.beathub.multiarenas.delivery.payment.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ParticipanteInicialDto {

    @NotNull(message = "El usuarioId es obligatorio")
    private Long usuarioId;

    private BigDecimal montoAsignado;

    private BigDecimal porcentaje;

    private String nombre;

    private String email;
}
