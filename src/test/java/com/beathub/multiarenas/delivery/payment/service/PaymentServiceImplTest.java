package com.beathub.multiarenas.delivery.payment.service;

import com.beathub.multiarenas.delivery.payment.client.CredibancoRestClient;
import com.beathub.multiarenas.delivery.payment.client.OrderingServiceClient;
import com.beathub.multiarenas.delivery.payment.config.CredibancoProperties;
import com.beathub.multiarenas.delivery.payment.entity.*;
import com.beathub.multiarenas.delivery.payment.repository.*;
import com.beathub.multiarenas.delivery.payment.service.impl.PaymentServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentServiceImplTest {

    @Mock
    private PedidoPagoRepository pedidoPagoRepository;

    @Mock
    private TipoPagoRepository tipoPagoRepository;

    @Mock
    private MedioPagoRepository medioPagoRepository;

    @Mock
    private GrupoPagoUsuarioRepository grupoPagoUsuarioRepository;

    @Mock
    private GrupoPagoRepository grupoPagoRepository;

    @Mock
    private CredibancoRestClient credibancoClient;

    @Mock
    private OrderingServiceClient orderingClient;

    @Mock
    private CredibancoProperties credibancoProperties;

    @Mock
    private HmacSignatureService hmacSignatureService;

    @InjectMocks
    private PaymentServiceImpl paymentService;

    private GrupoPago grupoPago;
    private GrupoPagoUsuario grupoPagoUsuario;
    private PedidoPago pedidoPago;

    @BeforeEach
    void setUp() {
        grupoPago = GrupoPago.builder()
                .id(10L)
                .pedidoId(99L)
                .codigoUnico("VACA-ABC123")
                .nombre("Vaca Amigos")
                .valorTotal(new BigDecimal("100000.00"))
                .totalPagado(BigDecimal.ZERO)
                .saldoPendiente(new BigDecimal("100000.00"))
                .estadoId(1)
                .build();

        grupoPagoUsuario = GrupoPagoUsuario.builder()
                .id(1L)
                .grupoPago(grupoPago)
                .usuarioId(5L)
                .montoAsignado(new BigDecimal("50000.00"))
                .montoPagado(BigDecimal.ZERO)
                .esLider(true)
                .estadoId(1)
                .build();

        pedidoPago = PedidoPago.builder()
                .id(501L)
                .pedidoId(99L)
                .usuarioId(5L)
                .grupoPagoUsuario(grupoPagoUsuario)
                .referenciaPago("PAY-99-001")
                .credibancoOrderId("CRED-99-001")
                .monto(new BigDecimal("50000.00"))
                .estadoId(1)
                .build();
    }

    @Test
    @DisplayName("Callback Credibanco con pago parcial de grupo: actualiza saldo y mantiene pedido en espera")
    void processCredibancoCallback_AporteParcial() {
        when(pedidoPagoRepository.findByReferenciaPago("PAY-99-001")).thenReturn(Optional.of(pedidoPago));

        paymentService.processCredibancoCallback("CRED-99-001", "PAY-99-001", "deposited", 1, "sig", "alias");

        assertEquals(2, pedidoPago.getEstadoId()); // Pago aprobado
        assertNotNull(pedidoPago.getFechaPago());

        // Aporte usuario actualizado
        assertEquals(new BigDecimal("50000.00"), grupoPagoUsuario.getMontoPagado());
        assertEquals(2, grupoPagoUsuario.getEstadoId()); // Usuario ya completó su cuota de 50.000

        // Grupo pago actualizado
        assertEquals(new BigDecimal("50000.00"), grupoPago.getTotalPagado());
        assertEquals(new BigDecimal("50000.00"), grupoPago.getSaldoPendiente());
        assertEquals(1, grupoPago.getEstadoId()); // Aún activo porque falta el 50%

        // No debe completar la orden general en Ordering aún
        verify(orderingClient, never()).updateOrderStatus(eq(99L), eq(2), any(), any());
    }

    @Test
    @DisplayName("Callback Credibanco que completa el 100% de la vaca: notifica a Ordering para pasar a producción")
    void processCredibancoCallback_AporteFinalCompleta100PorCiento() {
        // Pre-condición: ya se habían pagado 50.000 previamente
        grupoPago.setTotalPagado(new BigDecimal("50000.00"));
        grupoPago.setSaldoPendiente(new BigDecimal("50000.00"));

        when(pedidoPagoRepository.findByReferenciaPago("PAY-99-001")).thenReturn(Optional.of(pedidoPago));

        paymentService.processCredibancoCallback("CRED-99-001", "PAY-99-001", "deposited", 1, "sig", "alias");

        assertEquals(2, pedidoPago.getEstadoId());
        assertEquals(0, new BigDecimal("100000.00").compareTo(grupoPago.getTotalPagado()));
        assertEquals(0, BigDecimal.ZERO.compareTo(grupoPago.getSaldoPendiente()));
        assertEquals(2, grupoPago.getEstadoId()); // 2: Completado (100% Pagado)

        // Debe notificar a Ordering que el pedido fue completado al 100%
        verify(orderingClient).updateOrderStatus(eq(99L), eq(2), contains("Vaca/Pago grupal 100% completado"), isNull());
    }
}
