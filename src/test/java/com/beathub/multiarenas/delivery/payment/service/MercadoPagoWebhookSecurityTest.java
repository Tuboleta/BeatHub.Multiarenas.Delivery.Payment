package com.beathub.multiarenas.delivery.payment.service;

import com.beathub.multiarenas.delivery.payment.client.MercadoPagoRestClient;
import com.beathub.multiarenas.delivery.payment.client.dto.mercadopago.MpPaymentDetailResponse;
import com.beathub.multiarenas.delivery.payment.config.MercadoPagoProperties;
import com.beathub.multiarenas.delivery.payment.entity.PedidoPago;
import com.beathub.multiarenas.delivery.payment.exception.UnauthorizedException;
import com.beathub.multiarenas.delivery.payment.repository.PedidoPagoRepository;
import com.beathub.multiarenas.delivery.payment.service.impl.MercadoPagoServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MercadoPagoWebhookSecurityTest {

    @Mock
    private PedidoPagoRepository pedidoPagoRepository;
    @Mock
    private MercadoPagoRestClient mercadoPagoClient;
    @Mock
    private MercadoPagoProperties mercadoPagoProperties;

    private final HmacSignatureService hmacSignatureService = new HmacSignatureService();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private MercadoPagoServiceImpl mercadoPagoService;

    private static final String SECRET = "test-webhook-secret-c01";

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(mercadoPagoService, "hmacSignatureService", hmacSignatureService);
        ReflectionTestUtils.setField(mercadoPagoService, "objectMapper", objectMapper);
    }

    @Test
    @DisplayName("C-01: Rechaza webhook con 401 si webhookSecret no está configurado (Fail-Closed)")
    void processWebhook_RechazaSiSecretNoConfigurado() {
        when(mercadoPagoProperties.getWebhookSecret()).thenReturn("");

        assertThrows(UnauthorizedException.class, () ->
                mercadoPagoService.processWebhook("{\"data\":{\"id\":\"12345\"}}", "ts=1,v1=abc", "req-1", "12345"));
    }

    @Test
    @DisplayName("C-01: Rechaza webhook con 401 si faltan headers x-signature o x-request-id")
    void processWebhook_RechazaSinHeadersFirma() {
        when(mercadoPagoProperties.getWebhookSecret()).thenReturn(SECRET);

        assertThrows(UnauthorizedException.class, () ->
                mercadoPagoService.processWebhook("{\"data\":{\"id\":\"12345\"}}", null, "req-1", "12345"));
    }

    @Test
    @DisplayName("C-01: Rechaza webhook con 401 si la firma HMAC es inválida")
    void processWebhook_RechazaFirmaInvalida() {
        when(mercadoPagoProperties.getWebhookSecret()).thenReturn(SECRET);
        long nowTs = System.currentTimeMillis() / 1000;
        String badSig = "ts=" + nowTs + ",v1=DEADBEEF";

        assertThrows(UnauthorizedException.class, () ->
                mercadoPagoService.processWebhook("{\"data\":{\"id\":\"12345\"}}", badSig, "req-1", "12345"));
    }

    @Test
    @DisplayName("C-01: Rechaza webhook con 401 si el timestamp supera la ventana anti-replay de 5 minutos")
    void processWebhook_RechazaTimestampExpirado() {
        when(mercadoPagoProperties.getWebhookSecret()).thenReturn(SECRET);
        long oldTs = (System.currentTimeMillis() / 1000) - 600;
        String manifest = "id:12345;request-id:req-1;ts:" + oldTs + ";";
        String sig = hmacSignatureService.generateHMacSHA256(SECRET, manifest);
        String header = "ts=" + oldTs + ",v1=" + sig;

        assertThrows(UnauthorizedException.class, () ->
                mercadoPagoService.processWebhook("{\"data\":{\"id\":\"12345\"}}", header, "req-1", "12345"));
    }

    @Test
    @DisplayName("C-01: Idempotencia — webhook válido sobre pago ya APROBADO no vuelve a consultar MP")
    void processWebhook_IdempotenciaSiYaAprobado() {
        when(mercadoPagoProperties.getWebhookSecret()).thenReturn(SECRET);
        long nowTs = System.currentTimeMillis() / 1000;
        String dataId = "998877";
        String reqId = "req-idem-1";
        String manifest = "id:" + dataId + ";request-id:" + reqId + ";ts:" + nowTs + ";";
        String sig = hmacSignatureService.generateHMacSHA256(SECRET, manifest);
        String header = "ts=" + nowTs + ",v1=" + sig;

        PedidoPago aprobado = PedidoPago.builder()
                .id(10L)
                .pedidoId(500L)
                .arenaId("1")
                .usuarioId(35L)
                .monto(new BigDecimal("15000.00"))
                .moneda("COP")
                .referenciaPago("MP-ORD-500")
                .mpPaymentId(dataId)
                .estadoId(PedidoPago.ESTADO_APROBADO)
                .build();

        when(pedidoPagoRepository.findByMpOrderId(dataId)).thenReturn(Optional.empty());
        when(pedidoPagoRepository.findByReferenciaPago(dataId)).thenReturn(Optional.empty());
        when(pedidoPagoRepository.findByMpPaymentId(dataId)).thenReturn(Optional.of(aprobado));

        assertDoesNotThrow(() ->
                mercadoPagoService.processWebhook("{\"type\":\"payment\",\"data\":{\"id\":\"998877\"}}", header, reqId, dataId));

        verify(mercadoPagoClient, never()).getPayment(any(), any(), any());
        verify(mercadoPagoClient, never()).getOrder(any(), any(), any());
        assertEquals(PedidoPago.ESTADO_APROBADO, aprobado.getEstadoId());
    }

    @Test
    @DisplayName("C-01: Integridad — rechaza aprobación si el monto reportado por MP es menor al monto del pedido")
    void processWebhook_NoApruebaSiMontoEsMenorAlEsperado() {
        when(mercadoPagoProperties.getWebhookSecret()).thenReturn(SECRET);
        long nowTs = System.currentTimeMillis() / 1000;
        String dataId = "112233";
        String reqId = "req-amt-1";
        String manifest = "id:" + dataId + ";request-id:" + reqId + ";ts:" + nowTs + ";";
        String sig = hmacSignatureService.generateHMacSHA256(SECRET, manifest);
        String header = "ts=" + nowTs + ",v1=" + sig;

        PedidoPago pendiente = PedidoPago.builder()
                .id(11L)
                .pedidoId(501L)
                .arenaId("1")
                .usuarioId(35L)
                .monto(new BigDecimal("15000.00"))
                .moneda("COP")
                .referenciaPago("MP-ORD-501")
                .mpPaymentId(dataId)
                .estadoId(PedidoPago.ESTADO_INICIADO)
                .build();

        when(pedidoPagoRepository.findByMpOrderId(dataId)).thenReturn(Optional.empty());
        when(pedidoPagoRepository.findByReferenciaPago(dataId)).thenReturn(Optional.empty());
        when(pedidoPagoRepository.findByMpPaymentId(dataId)).thenReturn(Optional.of(pendiente));

        MpPaymentDetailResponse pagoManipulado = MpPaymentDetailResponse.builder()
                .id(dataId)
                .status("approved")
                .statusDetail("accredited")
                .externalReference("MP-ORD-501")
                .transactionAmount(new BigDecimal("1.00"))
                .currencyId("COP")
                .build();
        when(mercadoPagoClient.getPayment(eq(dataId), any(), any())).thenReturn(pagoManipulado);

        assertDoesNotThrow(() ->
                mercadoPagoService.processWebhook("{\"type\":\"payment\",\"data\":{\"id\":\"112233\"}}", header, reqId, dataId));

        assertEquals(PedidoPago.ESTADO_INICIADO, pendiente.getEstadoId(),
                "El pago NO debe pasar a APROBADO cuando el monto cobrado en MP es inferior al monto esperado");
    }
}
