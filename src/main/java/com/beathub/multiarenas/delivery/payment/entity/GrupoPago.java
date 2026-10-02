package com.beathub.multiarenas.delivery.payment.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "grupo_pago", schema = "payment")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GrupoPago {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "codigo_unico", nullable = false, length = 50, unique = true)
    private String codigoUnico;

    @Column(name = "nombre", nullable = false, length = 100)
    private String nombre;

    @Column(name = "arena_id", length = 50)
    private String arenaId;

    @Column(name = "pedido_id")
    private Long pedidoId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tipo_grupo_pago_id")
    private TipoGrupoPago tipoGrupoPago;

    @Column(name = "es_permanente", nullable = false)
    private Boolean esPermanente;

    @Column(name = "modalidad_division", length = 30)
    private String modalidadDivision; // "POR_PARTES_IGUALES" o "LIBRE_PAGO"

    @Column(name = "cantidad_personas")
    private Integer cantidadPersonas;

    @Column(name = "monto_preasignado", precision = 12, scale = 2)
    private BigDecimal montoPreasignado;

    @Column(name = "fecha_expiracion")
    private LocalDateTime fechaExpiracion;

    @Column(name = "valor_total", nullable = false, precision = 12, scale = 2)
    private BigDecimal valorTotal;

    @Column(name = "total_pagado", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalPagado;

    @Column(name = "saldo_pendiente", nullable = false, precision = 12, scale = 2)
    private BigDecimal saldoPendiente;

    @Column(name = "estado_id", nullable = false)
    private Integer estadoId; // 1: Creado/Activo, 2: Completado (100% Pagado), 3: Expirado, 4: Cancelado

    @Column(name = "creacion_fecha", nullable = false, updatable = false)
    private LocalDateTime creacionFecha;

    @Column(name = "creacion_usuario")
    private Long creacionUsuario;

    @Column(name = "actualizacion_fecha")
    private LocalDateTime actualizacionFecha;

    @Column(name = "actualizacion_usuario")
    private Long actualizacionUsuario;

    @PrePersist
    protected void onCreate() {
        if (creacionFecha == null) {
            creacionFecha = LocalDateTime.now();
        }
        if (estadoId == null) {
            estadoId = 1;
        }
        if (esPermanente == null) {
            esPermanente = false;
        }
        if (valorTotal == null) {
            valorTotal = BigDecimal.ZERO;
        }
        if (totalPagado == null) {
            totalPagado = BigDecimal.ZERO;
        }
        if (saldoPendiente == null) {
            saldoPendiente = valorTotal.subtract(totalPagado).max(BigDecimal.ZERO);
        }
    }

    @PreUpdate
    protected void onUpdate() {
        actualizacionFecha = LocalDateTime.now();
    }
}
