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
public class SaveCardBindingRequest {

    @NotBlank(message = "El bindingId es obligatorio")
    private String bindingId;

    private String tarjetaEnmascarada;
    private String franquicia;
    private String expiracion; // MM/YY o YYYYMM
    private String titular;
    @Builder.Default
    private Boolean esPredeterminada = false;
}
