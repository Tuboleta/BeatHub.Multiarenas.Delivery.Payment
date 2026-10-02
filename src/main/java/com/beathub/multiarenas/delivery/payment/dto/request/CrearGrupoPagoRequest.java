package com.beathub.multiarenas.delivery.payment.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
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
public class CrearGrupoPagoRequest {

    @NotNull(message = "El pedidoId es obligatorio")
    private Long pedidoId;

    /**
     * Opcional: ID de un grupo permanente ya existente (ej. Palco fijo, familia)
     * para reutilizarlo en este nuevo pedido.
     */
    private Long grupoPagoPermanenteId;

    /**
     * Opcional: Código único del grupo permanente a reutilizar (ej: "VACA-PALCO10").
     */
    private String codigoGrupoPermanente;

    @Size(max = 100, message = "El nombre no puede superar los 100 caracteres")
    private String nombre;

    @Builder.Default
    private Integer tipoGrupoPagoId = 1; // 1: TEMPORAL_DIA, 2: PERMANENTE, 3: TEMPORAL_EVENTO

    @Builder.Default
    private Boolean esPermanente = false;

    private LocalDateTime fechaExpiracion;

    /**
     * Modalidad de división del pago:
     * - "POR_PARTES_IGUALES" (o "EQUITATIVA"): Cuotas fijas preasignadas por el sistema (valorTotal / cantidadPersonas).
     * - "LIBRE_PAGO" (o "LIBRE"): Aporte libre donde cada usuario asigna el valor dentro del rango del valor total.
     */
    @Builder.Default
    private String divisionTipo = "POR_PARTES_IGUALES";

    private Integer cantidadPersonas;

    private BigDecimal valorTotal;

    private List<ParticipanteInicialDto> participantes;

    private String observaciones;
}
