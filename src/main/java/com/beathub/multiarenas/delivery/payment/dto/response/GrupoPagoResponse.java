package com.beathub.multiarenas.delivery.payment.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GrupoPagoResponse {

    private Long id;
    private String codigoUnico;
    private String nombre;
    private String arenaId;
    private Long pedidoId;
    private Integer tipoGrupoPagoId;
    private String tipoGrupoPagoNombre;
    private Boolean esPermanente;
    private LocalDateTime fechaExpiracion;
    private BigDecimal valorTotal;
    private BigDecimal totalPagado;
    private BigDecimal saldoPendiente;
    private BigDecimal porcentajeAvance;
    private Integer estadoId; // 1: Creado/Activo, 2: Completado (100% Pagado), 3: Expirado, 4: Cancelado
    private String estadoNombre;
    private Boolean esLider;

    // Modalidad de pago y cuotas
    private String modalidadDivision; // "POR_PARTES_IGUALES" o "LIBRE_PAGO"
    private BigDecimal montoPreasignado; // Valor preasignado por el sistema en caso de partes iguales
    private Integer cantidadPersonas;
    private BigDecimal rangoAporteMinimo; // 0.01
    private BigDecimal rangoAporteMaximo; // saldoPendiente

    // Datos y código QR para lectura por el usuario / PWA
    private String qrCodeData; // URL completa legible por la cámara o app
    private String qrCodeImage; // Imagen PNG en Base64 (data:image/png;base64,...)
    private String enlaceInvitacion; // Ruta relativa /vaca/VACA-XXXXXX

    private GrupoPagoUsuarioResponse miParticipacion;
    private List<GrupoPagoUsuarioResponse> participantes;
    private List<PagoGrupoItemResponse> pagos;
    private LocalDateTime creacionFecha;
}
