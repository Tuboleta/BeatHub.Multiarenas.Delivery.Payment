package com.beathub.multiarenas.delivery.payment.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class VerifyCardRequest {

    @NotBlank(message = "El PAN es obligatorio")
    private String pan;

    @NotBlank(message = "El CVC es obligatorio")
    private String cvc;

    @NotBlank(message = "La expiración es obligatoria (YYYYMM)")
    private String expiry;
}
