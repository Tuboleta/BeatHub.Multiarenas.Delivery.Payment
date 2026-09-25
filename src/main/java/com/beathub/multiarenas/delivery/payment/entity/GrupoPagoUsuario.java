package com.beathub.multiarenas.delivery.payment.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "grupo_pago_usuario", schema = "payment")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GrupoPagoUsuario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "grupo_pago_id", nullable = false)
    private GrupoPago grupoPago;

    @Column(name = "usuario_id", nullable = false)
    private Long usuarioId;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal monto;

    @Column(name = "estado_id", nullable = false)
    private Integer estadoId;

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
    }

    @PreUpdate
    protected void onUpdate() {
        actualizacionFecha = LocalDateTime.now();
    }
}
