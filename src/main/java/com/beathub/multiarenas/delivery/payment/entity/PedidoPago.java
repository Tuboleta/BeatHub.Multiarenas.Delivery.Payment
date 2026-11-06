package com.beathub.multiarenas.delivery.payment.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "pedido_pago", schema = "payment")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PedidoPago {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "pedido_id", nullable = false)
    private Long pedidoId;

    @Column(name = "grupo_pedido_id", length = 100)
    private String grupoPedidoId;

    @Column(name = "arena_id", length = 50)
    private String arenaId;

    @Column(name = "usuario_id", nullable = false)
    private Long usuarioId;

    @Column(name = "credibanco_order_id", length = 100)
    private String credibancoOrderId;

    @Column(name = "mp_order_id", length = 100)
    private String mpOrderId;

    @Column(name = "mp_payment_id", length = 100)
    private String mpPaymentId;

    @Column(name = "init_point", columnDefinition = "TEXT")
    private String initPoint;

    @Column(name = "md_order", length = 100)
    private String mdOrder;

    @Column(name = "form_url", columnDefinition = "TEXT")
    private String formUrl;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tipo_pago_id")
    private TipoPago tipoPago;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "medio_pago_id")
    private MedioPago medioPago;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "grupo_pago_usuario_id")
    private GrupoPagoUsuario grupoPagoUsuario;

    @Column(name = "pasarela_id")
    private Integer pasarelaId; // 1: Mercado Pago, 2: Credibanco

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal monto;

    @Column(length = 10)
    private String moneda;

    @Column(name = "referencia_pago", nullable = false, length = 100, unique = true)
    private String referenciaPago;

    @Column(name = "fecha_pago")
    private LocalDateTime fechaPago;

    @Column(name = "action_code", length = 20)
    private String actionCode;

    @Column(name = "action_code_description", columnDefinition = "TEXT")
    private String actionCodeDescription;

    @Column(name = "auth_code", length = 20)
    private String authCode;

    @Column(name = "error_code", length = 20)
    private String errorCode;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "ip_cliente", length = 50)
    private String ipCliente;

    @Column(name = "json_params", columnDefinition = "TEXT")
    private String jsonParams;

    // Estados de Flujo 5 (Transacciones de Pago en public.estado)
    public static final int ESTADO_INICIADO = 22;
    public static final int ESTADO_APROBADO = 23;
    public static final int ESTADO_RECHAZADO = 24;
    public static final int ESTADO_REVERSADO = 25;
    public static final int ESTADO_ANULADO = 26;

    @Column(name = "estado_id", nullable = false)
    private Integer estadoId; // Flujo 5: 22=Iniciado, 23=Aprobado, 24=Rechazado, 25=Reversado, 26=Anulado

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
        if (moneda == null) {
            moneda = "COP";
        }
        if (estadoId == null) {
            estadoId = ESTADO_INICIADO; // 22: INICIADO (Flujo 5)
        }
    }

    @PreUpdate
    protected void onUpdate() {
        actualizacionFecha = LocalDateTime.now();
    }
}
