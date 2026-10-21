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
import java.util.Map;
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
    private ClientePagoRepository clientePagoRepository;

    @Mock
    private TarjetaClientePagoRepository tarjetaClientePagoRepository;

    @Mock
    private CredibancoRestClient credibancoClient;

    @Mock
    private OrderingServiceClient orderingClient;

    @Mock
    private CredibancoProperties credibancoProperties;

    @Mock
    private com.beathub.multiarenas.delivery.payment.client.NotificationServiceClient notificationServiceClient;

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

    @Test
    @DisplayName("Pago con token / One-Click exitoso: debita tarjeta guardada vía paymentOrderBinding y aprueba pedido")
    void payWithToken_Exitoso() {
        com.beathub.multiarenas.delivery.payment.client.dto.CredibancoRegisterResponse regResp =
                new com.beathub.multiarenas.delivery.payment.client.dto.CredibancoRegisterResponse();
        regResp.setOrderId("CRED-ORDER-TOKEN-123");
        regResp.setFormUrl("https://ecouat.credibanco.com/payment/merchants/test/payment_es.html?mdOrder=CRED-ORDER-TOKEN-123");
        regResp.setErrorCode("0");

        com.beathub.multiarenas.delivery.payment.client.dto.CredibancoPaymentOrderResponse payResp =
                new com.beathub.multiarenas.delivery.payment.client.dto.CredibancoPaymentOrderResponse();
        payResp.setErrorCode("0");
        payResp.setRbsOrderId("AUTH-TOKEN-789");

        when(tipoPagoRepository.findById(anyInt())).thenReturn(Optional.of(new TipoPago(1, "Pago Único", "Descripción", 1, LocalDateTime.now(), 1L)));
        when(medioPagoRepository.findById(anyInt())).thenReturn(Optional.of(new MedioPago(1, "Tarjetas", 1, LocalDateTime.now(), 1L)));
        when(pedidoPagoRepository.save(any(PedidoPago.class))).thenAnswer(i -> i.getArgument(0));

        when(credibancoClient.registerOrder(
                any(), any(), any(), any(), any(), any(), eq("ARENA_1_USR_5"), isNull(), eq("AUTO_PAYMENT"), eq("1"), eq(5L)
        )).thenReturn(regResp);

        when(credibancoClient.paymentOrderBinding(
                eq("CRED-ORDER-TOKEN-123"), eq("BIND-XYZ-999"), eq("123"), eq("127.0.0.1"), eq("1"), eq(5L)
        )).thenReturn(payResp);

        com.beathub.multiarenas.delivery.payment.client.dto.CredibancoStatusResponse statusResp =
                new com.beathub.multiarenas.delivery.payment.client.dto.CredibancoStatusResponse();
        statusResp.setOrderStatus(2);
        statusResp.setAuthCode("123456");
        statusResp.setTerminalId("00035992");

        when(credibancoClient.getOrderStatusExtended(
                eq("CRED-ORDER-TOKEN-123"), any(), eq("1"), eq(5L)
        )).thenReturn(statusResp);

        com.beathub.multiarenas.delivery.payment.dto.request.PayWithTokenRequest request =
                com.beathub.multiarenas.delivery.payment.dto.request.PayWithTokenRequest.builder()
                        .pedidoId(99L)
                        .monto(new BigDecimal("45000.00"))
                        .bindingId("BIND-XYZ-999")
                        .cvc("123")
                        .tipoPagoId(1)
                        .description("Pago One-Click Pedido #99")
                        .build();

        com.beathub.multiarenas.delivery.payment.dto.response.PaymentTokenResponse response =
                paymentService.payWithToken(request, "1", 5L, "127.0.0.1");

        assertNotNull(response);
        assertEquals(2, response.getEstadoId());
        assertEquals("APROBADO", response.getEstadoNombre());
        assertEquals("123456", response.getAuthCode());
        assertFalse(response.isRequiere3ds());
        verify(orderingClient).updateOrderStatus(eq(99L), eq(2), any(), any(), eq("123456"), eq("00035992"), any());
    }

    @Test
    @DisplayName("Listar tarjetas guardadas del usuario: devuelve tarjetas activas tokenizadas")
    void getUserCards_Exitoso() {
        ClientePago cp = ClientePago.builder()
                .id(1L)
                .arenaUsuarioId(5L)
                .bindingId("BIND-XYZ-999")
                .tarjetaEnmascarada("411111******1111")
                .estadoId(1)
                .build();

        TarjetaClientePago tcp = TarjetaClientePago.builder()
                .id(100L)
                .clientePago(cp)
                .tarjetaId("BIND-XYZ-999")
                .tarjetaEnmascarada("411111******1111")
                .franquicia("VISA")
                .expiracion("12/28")
                .titular("CARLOS GOMEZ")
                .esPredeterminada(true)
                .estadoId(1)
                .creacionFecha(LocalDateTime.now())
                .build();

        when(tarjetaClientePagoRepository.findByClientePagoArenaUsuarioIdAndEstadoId(5L, 1))
                .thenReturn(java.util.List.of(tcp));

        java.util.List<com.beathub.multiarenas.delivery.payment.dto.response.TarjetaGuardadaResponse> tarjetas =
                paymentService.getUserCards("1", 5L);

        assertNotNull(tarjetas);
        assertEquals(1, tarjetas.size());
        assertEquals("BIND-XYZ-999", tarjetas.get(0).getBindingId());
        assertEquals("411111******1111", tarjetas.get(0).getTarjetaEnmascarada());
        assertEquals("VISA", tarjetas.get(0).getFranquicia());
        assertTrue(tarjetas.get(0).getEsPredeterminada());
    }

    @Test
    @DisplayName("Tokenizar tarjeta directa exitoso: valida tarjeta, envía VERIFY a Credibanco y persiste token legítimo")
    void tokenizeCard_Exitoso() {
        com.beathub.multiarenas.delivery.payment.client.dto.CredibancoRegisterResponse regResp =
                new com.beathub.multiarenas.delivery.payment.client.dto.CredibancoRegisterResponse();
        regResp.setOrderId("CRED-VERIFY-123");

        com.beathub.multiarenas.delivery.payment.client.dto.CredibancoPaymentOrderResponse payResp =
                new com.beathub.multiarenas.delivery.payment.client.dto.CredibancoPaymentOrderResponse();
        payResp.setErrorCode("0");

        com.beathub.multiarenas.delivery.payment.client.dto.CredibancoStatusResponse statusResp =
                new com.beathub.multiarenas.delivery.payment.client.dto.CredibancoStatusResponse();
        statusResp.setOrderStatus(4); // 4 = Reversado tras VERIFY según manual Credibanco
        statusResp.setActionCode("0");
        statusResp.setBindingId("BIND-LEGIT-CRED-999");
        statusResp.setCardAuthInfo(java.util.Map.of(
                "maskedPan", "411111******1111",
                "paymentSystem", "VISA",
                "cardholderName", "CARLOS GOMEZ"
        ));

        when(credibancoProperties.getDefaultReturnUrl()).thenReturn("http://localhost:3000/return");
        when(credibancoProperties.getDefaultFailUrl()).thenReturn("http://localhost:3000/fail");

        when(credibancoClient.registerOrder(any(), eq(BigDecimal.ZERO), any(), any(), any(), isNull(), eq("ARENA_1_USR_5"), eq("VERIFY"), eq("1"), eq(5L)))
                .thenReturn(regResp);

        when(credibancoClient.paymentOrder(eq("CRED-VERIFY-123"), eq("4111111111111111"), eq("123"), eq("2028"), eq("12"), eq("CARLOS GOMEZ"), eq("U"), any(), any(), eq("1"), eq(5L)))
                .thenReturn(payResp);

        when(credibancoClient.getOrderStatusExtended(eq("CRED-VERIFY-123"), any(), eq("1"), eq(5L)))
                .thenReturn(statusResp);

        when(clientePagoRepository.findByClienteReferencia(any()))
                .thenReturn(Optional.empty());
        when(clientePagoRepository.save(any(ClientePago.class))).thenAnswer(i -> {
            ClientePago cp = i.getArgument(0);
            cp.setId(50L);
            return cp;
        });

        when(tarjetaClientePagoRepository.save(any(TarjetaClientePago.class))).thenAnswer(i -> {
            TarjetaClientePago tcp = i.getArgument(0);
            tcp.setId(101L);
            return tcp;
        });

        com.beathub.multiarenas.delivery.payment.dto.request.TokenizarTarjetaRequest request =
                com.beathub.multiarenas.delivery.payment.dto.request.TokenizarTarjetaRequest.builder()
                        .pan("4111111111111111")
                        .cvc("123")
                        .mesVencimiento("12")
                        .anioVencimiento("2028")
                        .nombreTitular("Carlos Gomez")
                        .esPredeterminada(true)
                        .build();

        var response = paymentService.tokenizeCard(request, "1", 5L, "127.0.0.1");

        assertNotNull(response);
        assertEquals("BIND-LEGIT-CRED-999", response.getBindingId());
        assertEquals("411111******1111", response.getTarjetaEnmascarada());
        assertEquals("VISA", response.getFranquicia());
        assertEquals("CARLOS GOMEZ", response.getTitular());
        assertTrue(response.getEsPredeterminada());
    }

    @Test
    @DisplayName("Tokenizar tarjeta con PAN inválido (Luhn): lanza BadRequestException")
    void tokenizeCard_LuhnInvalido_LanzaBadRequestException() {
        com.beathub.multiarenas.delivery.payment.dto.request.TokenizarTarjetaRequest request =
                com.beathub.multiarenas.delivery.payment.dto.request.TokenizarTarjetaRequest.builder()
                        .pan("4111111111111119") // PAN con dígito de verificación inválido
                        .cvc("123")
                        .mesVencimiento("12")
                        .anioVencimiento("2028")
                        .nombreTitular("Carlos Gomez")
                        .build();

        assertThrows(com.beathub.multiarenas.delivery.payment.exception.BadRequestException.class, () ->
                paymentService.tokenizeCard(request, "1", 5L, "127.0.0.1"));
    }

    @Test
    @DisplayName("Tokenizar tarjeta con titular sin apellido: lanza BadRequestException")
    void tokenizeCard_TitularSinApellido_LanzaBadRequestException() {
        com.beathub.multiarenas.delivery.payment.dto.request.TokenizarTarjetaRequest request =
                com.beathub.multiarenas.delivery.payment.dto.request.TokenizarTarjetaRequest.builder()
                        .pan("4111111111111111")
                        .cvc("123")
                        .mesVencimiento("12")
                        .anioVencimiento("2028")
                        .nombreTitular("Carlos") // Falta apellido
                        .build();

        assertThrows(com.beathub.multiarenas.delivery.payment.exception.BadRequestException.class, () ->
                paymentService.tokenizeCard(request, "1", 5L, "127.0.0.1"));
    }

    @Test
    @DisplayName("initiatePayment con guardarTarjeta=true: envía clientId y no fuerza AUTO_PAYMENT por defecto (evita error 13)")
    void initiatePayment_ConGuardarTarjeta_EnviaClientIdSinAutoPaymentPorDefecto() {
        com.beathub.multiarenas.delivery.payment.client.dto.CredibancoRegisterResponse regResp =
                new com.beathub.multiarenas.delivery.payment.client.dto.CredibancoRegisterResponse();
        regResp.setOrderId("CRED-INIT-123");
        regResp.setFormUrl("https://ecouat.credibanco.com/payment/form?mdOrder=CRED-INIT-123");

        when(tipoPagoRepository.findById(anyInt())).thenReturn(Optional.of(new TipoPago(1, "Pago Único", "Desc", 1, LocalDateTime.now(), 1L)));
        when(pedidoPagoRepository.save(any(PedidoPago.class))).thenAnswer(i -> {
            PedidoPago pp = i.getArgument(0);
            pp.setId(1001L);
            return pp;
        });

        when(credibancoClient.registerOrder(
                anyString(),
                eq(new BigDecimal("18600.00")),
                anyString(),
                anyString(),
                anyString(),
                anyString(),
                eq("ARENA_1_USR_5"),
                isNull(),
                isNull(),
                eq("1"),
                eq(5L)
        )).thenReturn(regResp);

        com.beathub.multiarenas.delivery.payment.dto.request.InitPaymentRequest req = new com.beathub.multiarenas.delivery.payment.dto.request.InitPaymentRequest();
        req.setPedidoId(44L);
        req.setMonto(new BigDecimal("18600.00"));
        req.setGuardarTarjeta(true);

        var resp = paymentService.initiatePayment(req, "1", 5L);

        assertNotNull(resp);
        assertEquals("CRED-INIT-123", resp.getCredibancoOrderId());
        verify(credibancoClient).registerOrder(
                anyString(),
                eq(new BigDecimal("18600.00")),
                anyString(),
                anyString(),
                anyString(),
                anyString(),
                eq("ARENA_1_USR_5"),
                isNull(),
                isNull(),
                eq("1"),
                eq(5L)
        );
    }

    @Test
    @DisplayName("initiatePayment con features explícito en extraParams: envía el feature solicitado")
    void initiatePayment_ConFeaturesExplicito_EnviaFeatures() {
        com.beathub.multiarenas.delivery.payment.client.dto.CredibancoRegisterResponse regResp =
                new com.beathub.multiarenas.delivery.payment.client.dto.CredibancoRegisterResponse();
        regResp.setOrderId("CRED-INIT-123");
        regResp.setFormUrl("https://ecouat.credibanco.com/payment/form?mdOrder=CRED-INIT-123");

        when(tipoPagoRepository.findById(anyInt())).thenReturn(Optional.of(new TipoPago(1, "Pago Único", "Desc", 1, LocalDateTime.now(), 1L)));
        when(pedidoPagoRepository.save(any(PedidoPago.class))).thenAnswer(i -> {
            PedidoPago pp = i.getArgument(0);
            pp.setId(1001L);
            return pp;
        });

        when(credibancoClient.registerOrder(
                anyString(),
                eq(new BigDecimal("18600.00")),
                anyString(),
                anyString(),
                anyString(),
                anyString(),
                eq("ARENA_1_USR_5"),
                isNull(),
                eq("AUTO_PAYMENT"),
                eq("1"),
                eq(5L)
        )).thenReturn(regResp);

        com.beathub.multiarenas.delivery.payment.dto.request.InitPaymentRequest req = new com.beathub.multiarenas.delivery.payment.dto.request.InitPaymentRequest();
        req.setPedidoId(44L);
        req.setMonto(new BigDecimal("18600.00"));
        req.setGuardarTarjeta(true);
        req.setExtraParams(Map.of("features", "AUTO_PAYMENT"));

        var resp = paymentService.initiatePayment(req, "1", 5L);

        assertNotNull(resp);
        assertEquals("CRED-INIT-123", resp.getCredibancoOrderId());
        verify(credibancoClient).registerOrder(
                anyString(),
                eq(new BigDecimal("18600.00")),
                anyString(),
                anyString(),
                anyString(),
                anyString(),
                eq("ARENA_1_USR_5"),
                isNull(),
                eq("AUTO_PAYMENT"),
                eq("1"),
                eq(5L)
        );
    }
}
