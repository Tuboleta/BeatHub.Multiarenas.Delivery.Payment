package com.beathub.multiarenas.delivery.payment.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TarjetaGuardadaResponse {
    private Long id;
    private Long clientePagoId;
    private String bindingId;
    private String tarjetaEnmascarada;
    private String franquicia;
    private String expiracion;
    private String titular;
    private Boolean esPredeterminada;
    private Integer estadoId;
    private LocalDateTime creacionFecha;
}
