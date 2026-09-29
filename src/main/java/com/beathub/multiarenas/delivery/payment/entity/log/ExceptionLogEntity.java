package com.beathub.multiarenas.delivery.payment.entity.log;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "exception_log", schema = "public")
public class ExceptionLogEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "microservicio_id")
    private Integer microservicioId;

    @Column(name = "usuario_id")
    private Long usuarioId;

    @Column(name = "arena_id", length = 50)
    private String arenaId;

    @Column(name = "tipo", nullable = false, length = 150)
    private String tipo;

    @Column(name = "mensaje", nullable = false, columnDefinition = "TEXT")
    private String mensaje;

    @Column(name = "detalle", columnDefinition = "TEXT")
    private String detalle;

    @Column(name = "trace", columnDefinition = "TEXT")
    private String trace;

    @Column(name = "flujo_id")
    private Integer flujoId;

    @Column(name = "creacion_fecha", nullable = false)
    @Builder.Default
    private OffsetDateTime creacionFecha = OffsetDateTime.now();

    @Column(name = "creacion_usuario")
    private Long creacionUsuario;
}
