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
public class PagoGrupoItemResponse {

    private Long pedidoPagoId;
    private Long usuarioId;
    private String nombreUsuario;
    private BigDecimal monto;
    private LocalDateTime fechaPago;
    private Integer estadoId; // 1: Iniciado, 2: Aprobado, 3: Rechazado
    private String estadoNombre;
    private String referenciaPago;
    private String authCode;
}
