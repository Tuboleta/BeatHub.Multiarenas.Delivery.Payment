package com.beathub.multiarenas.delivery.payment.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TokenizarTarjetaRequest {

    @NotBlank(message = "El número de tarjeta (PAN) es obligatorio")
    @Pattern(regexp = "^[0-9]{13,19}$", message = "El PAN debe contener entre 13 y 19 dígitos numéricos")
    private String pan;

    @NotBlank(message = "El código CVC es obligatorio")
    @Pattern(regexp = "^[0-9]{3,4}$", message = "El CVC debe contener 3 o 4 dígitos numéricos")
    private String cvc;

    @NotBlank(message = "El mes de vencimiento es obligatorio")
    @Pattern(regexp = "^(0?[1-9]|1[0-2])$", message = "El mes de vencimiento debe estar entre 01 y 12")
    private String mesVencimiento;

    @NotBlank(message = "El año de vencimiento es obligatorio")
    @Pattern(regexp = "^(20[2-9][0-9]|[2-9][0-9])$", message = "El año de vencimiento debe ser válido (ej: 2026 o 26)")
    private String anioVencimiento;

    @NotBlank(message = "El nombre del titular es obligatorio")
    private String nombreTitular;

    private String email;

    private String alias;

    @Builder.Default
    private Boolean esPredeterminada = false;
}
