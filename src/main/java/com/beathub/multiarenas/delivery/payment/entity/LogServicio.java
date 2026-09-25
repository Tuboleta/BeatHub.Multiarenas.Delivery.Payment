package com.beathub.multiarenas.delivery.payment.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "log_servicios", schema = "public")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LogServicio {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "arena_id", length = 50)
    private String arenaId;

    @Column(name = "usuario_id")
    private Long usuarioId;

    @Column(name = "servicio_integracion_id", nullable = false)
    private Integer servicioIntegracionId; // 2: CREDIBANCO

    @Column(name = "microservicio_id", nullable = false)
    private Integer microservicioId; // 4: PAYMENT

    @Column(nullable = false, length = 150)
    private String operacion;

    @Column(columnDefinition = "TEXT")
    private String request;

    @Column(columnDefinition = "TEXT")
    private String response;

    @Column(name = "fecha_proceso", nullable = false)
    private LocalDateTime fechaProceso;

    @Column(name = "fecha_respuesta")
    private LocalDateTime fechaRespuesta;

    @Column(name = "duracion_ms")
    private Long duracionMs;

    @Column(name = "http_code")
    private Integer httpCode;

    @Column(length = 50)
    private String resultado;

    @Column(name = "estado_id", nullable = false)
    private Integer estadoId;

    @Column(name = "creacion_fecha", nullable = false, updatable = false)
    private LocalDateTime creacionFecha;

    @Column(name = "creacion_usuario")
    private Long creacionUsuario;

    @PrePersist
    protected void onCreate() {
        if (creacionFecha == null) {
            creacionFecha = LocalDateTime.now();
        }
        if (fechaProceso == null) {
            fechaProceso = LocalDateTime.now();
        }
        if (servicioIntegracionId == null) {
            servicioIntegracionId = 2; // CREDIBANCO
        }
        if (microservicioId == null) {
            microservicioId = 4; // PAYMENT
        }
        if (estadoId == null) {
            estadoId = 1;
        }
    }
}
