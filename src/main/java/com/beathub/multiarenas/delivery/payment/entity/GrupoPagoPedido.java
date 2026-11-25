package com.beathub.multiarenas.delivery.payment.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "grupo_pago_pedido", schema = "payment",
       uniqueConstraints = @UniqueConstraint(name = "uq_grupo_pago_pedido", columnNames = {"grupo_pago_id", "pedido_id"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GrupoPagoPedido {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "grupo_pago_id", nullable = false)
    private GrupoPago grupoPago;

    @Column(name = "pedido_id", nullable = false)
    private Long pedidoId;

    @Column(name = "creacion_fecha", nullable = false, updatable = false)
    private LocalDateTime creacionFecha;

    @PrePersist
    protected void onCreate() {
        if (creacionFecha == null) {
            creacionFecha = LocalDateTime.now();
        }
    }
}
