package com.beathub.multiarenas.delivery.payment.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class ProcessCardPaymentRequest {

    @NotNull(message = "El pedidoId es obligatorio")
    private Long pedidoId;

    @NotNull(message = "El monto es obligatorio")
    private BigDecimal monto;

    @NotBlank(message = "El PAN de la tarjeta es obligatorio")
    private String pan;

    @NotBlank(message = "El CVC es obligatorio")
    private String cvc;

    @NotBlank(message = "La fecha de expiración es obligatoria (YYYYMM)")
    private String expiry;

    @NotBlank(message = "El nombre del titular es obligatorio (NOMBRE APELLIDO)")
    private String cardholderName;

    private Integer cuotas = 1;
    private String email;
    private String phone;
}
