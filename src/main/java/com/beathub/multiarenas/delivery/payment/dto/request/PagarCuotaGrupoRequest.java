package com.beathub.multiarenas.delivery.payment.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PagarCuotaGrupoRequest {

    @DecimalMin(value = "0.01", message = "El monto debe ser superior a 0")
    private BigDecimal monto;

    private Integer medioPagoId;

    @NotBlank(message = "El returnUrl es obligatorio (URL de retorno tras pago exitoso)")
    private String returnUrl;

    @NotBlank(message = "El failUrl es obligatorio (URL de retorno tras pago fallido)")
    private String failUrl;

    private String description;

    private Map<String, Object> extraParams;
}
