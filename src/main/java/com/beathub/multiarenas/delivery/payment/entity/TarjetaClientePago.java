package com.beathub.multiarenas.delivery.payment.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "tarjeta_cliente_pago", schema = "payment")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TarjetaClientePago {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cliente_pago_id", nullable = false)
    private ClientePago clientePago;

    @Column(name = "tarjeta_id", length = 100)
    private String tarjetaId;

    @Column(name = "tarjeta_enmascarada", nullable = false, length = 30)
    private String tarjetaEnmascarada;

    @Column(length = 50)
    private String franquicia;

    @Column(length = 10)
    private String expiracion;

    @Column(length = 150)
    private String titular;

    @Column(name = "es_predeterminada")
    private Boolean esPredeterminada;

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
        if (estadoId == null) {
            estadoId = 1;
        }
        if (esPredeterminada == null) {
            esPredeterminada = false;
        }
    }
}
