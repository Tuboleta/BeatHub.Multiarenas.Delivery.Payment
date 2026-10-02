package com.beathub.multiarenas.delivery.payment.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GrupoPagoUsuarioResponse {

    private Long id;
    private Long grupoPagoId;
    private Long usuarioId;
    private String nombreUsuario;
    private String email;
    private BigDecimal montoAsignado;
    private BigDecimal montoPagado;
    private BigDecimal saldoPendiente;
    private BigDecimal porcentaje;
    private Boolean esLider;
    private Integer estadoId; // 1: Pendiente/Activo, 2: Pagado, 3: Retirado
    private String estadoNombre;
    private LocalDateTime creacionFecha;
}
