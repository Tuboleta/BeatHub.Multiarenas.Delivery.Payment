package com.beathub.multiarenas.delivery.payment.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "factura", schema = "payment")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Factura {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "pedido_id", nullable = false)
    private Long pedidoId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pedido_pago_id")
    private PedidoPago pedidoPago;

    @Column(name = "referencia_factura", nullable = false, length = 100, unique = true)
    private String referenciaFactura;

    @Column(name = "pasarela_id")
    private Integer pasarelaId;

    @Column(name = "fecha_emision", nullable = false)
    private LocalDateTime fechaEmision;

    @Column(name = "file_url", columnDefinition = "TEXT")
    private String fileUrl;

    @Column(name = "total_bruto", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalBruto;

    @Column(precision = 12, scale = 2)
    private BigDecimal iva;

    @Column(precision = 12, scale = 2)
    private BigDecimal iac;

    @Column(name = "total_neto", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalNeto;

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
        if (fechaEmision == null) {
            fechaEmision = LocalDateTime.now();
        }
        if (pasarelaId == null) {
            pasarelaId = 2; // Credibanco
        }
        if (estadoId == null) {
            estadoId = 1;
        }
    }
}
