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
    private String alias;
    private Boolean esPredeterminada;
    private Integer estadoId;
    private LocalDateTime creacionFecha;
    @Builder.Default
    private Boolean requiere3ds = false;
    private String redirect3dsUrl;
    private String mensaje;
}
