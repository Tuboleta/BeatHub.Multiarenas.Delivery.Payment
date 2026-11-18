package com.beathub.multiarenas.delivery.payment.service;

import com.beathub.multiarenas.delivery.payment.client.AuthServiceClient;
import com.beathub.multiarenas.delivery.payment.client.MercadoPagoRestClient;
import com.beathub.multiarenas.delivery.payment.client.NotificationServiceClient;
import com.beathub.multiarenas.delivery.payment.client.OrderingServiceClient;
import com.beathub.multiarenas.delivery.payment.client.dto.UserClientResponse;
import com.beathub.multiarenas.delivery.payment.client.dto.mercadopago.MpOrderRequest;
import com.beathub.multiarenas.delivery.payment.client.dto.mercadopago.MpOrderResponse;
import com.beathub.multiarenas.delivery.payment.config.MercadoPagoProperties;
import com.beathub.multiarenas.delivery.payment.dto.request.InitPaymentRequest;
import com.beathub.multiarenas.delivery.payment.entity.PedidoPago;
import com.beathub.multiarenas.delivery.payment.entity.TipoPago;
import com.beathub.multiarenas.delivery.payment.repository.*;
import com.beathub.multiarenas.delivery.payment.service.impl.MercadoPagoServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MercadoPagoServiceImplTest {

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
        private MercadoPagoRestClient mercadoPagoClient;

        @Mock
        private OrderingServiceClient orderingClient;

        @Mock
        private NotificationServiceClient notificationServiceClient;

        @Mock
        private AuthServiceClient authServiceClient;

        @Mock
        private MercadoPagoProperties mercadoPagoProperties;

        @Mock
        private HmacSignatureService hmacSignatureService;

        private ObjectMapper objectMapper = new ObjectMapper();

        @InjectMocks
        private MercadoPagoServiceImpl mercadoPagoService;

        @BeforeEach
        void setUp() {
                org.springframework.test.util.ReflectionTestUtils.setField(mercadoPagoService, "objectMapper",
                                objectMapper);
                when(mercadoPagoProperties.getCurrency()).thenReturn("COP");
                when(mercadoPagoProperties.getDefaultReturnUrl())
                                .thenReturn("http://localhost:3000/checkout/payment-return");
                when(mercadoPagoProperties.getDefaultFailUrl())
                                .thenReturn("http://localhost:3000/checkout/payment-return?error=1");
                when(tipoPagoRepository.findById(1))
                                .thenReturn(Optional.of(TipoPago.builder().id(1).nombre("PAGO_UNICO").build()));
        }

        @Test
        @DisplayName("initiateOrder incluye payer.phone y payer.address para PSE Avanza y additional_info para homologación")
        void initiateOrder_PseAvanzaYDatosIndustria_ConfiguradosCorrectamente() {
                // Arrange
                InitPaymentRequest request = new InitPaymentRequest();
                request.setPedidoId(101L);
                request.setMonto(new BigDecimal("45000.00"));
                request.setTipoPagoId(1);
                request.setDescription("Compra Festival DAVIarena");

                when(pedidoPagoRepository.save(any(PedidoPago.class))).thenAnswer(invocation -> {
                        PedidoPago p = invocation.getArgument(0);
                        p.setId(888L);
                        return p;
                });

                UserClientResponse mockUser = UserClientResponse.builder()
                                .usuarioId(5L)
                                .email("carlos@example.com")
                                .nombres("Carlos")
                                .apellidos("Gómez")
                                .telefono("3109876543")
                                .numeroDocumento("1020304050")
                                .ssoProvider("TUBOLETA")
                                .creacionFecha(LocalDateTime.of(2023, 6, 15, 10, 0))
                                .direccion("Cra 15 # 93-60")
                                .ciudad("Bogotá")
                                .codigoPostal("110111")
                                .build();
                when(authServiceClient.getUsuario(5L)).thenReturn(Optional.of(mockUser));

                when(pedidoPagoRepository.findByUsuarioId(5L)).thenReturn(Collections.emptyList());

                MpOrderResponse mockResp = new MpOrderResponse();
                mockResp.setId("MP-ORD-TEST-123");
                mockResp.setCheckoutUrl("https://www.mercadopago.com.co/checkout/v1/redirect?pref_id=123");
                when(mercadoPagoClient.createOrder(any(MpOrderRequest.class), any(), any(), any()))
                                .thenReturn(mockResp);

                // Act
                var result = mercadoPagoService.initiateOrder(request, "daviarena", 5L);

                // Assert
                assertNotNull(result);
                assertEquals("MP-ORD-TEST-123", result.getOrderId());

                ArgumentCaptor<MpOrderRequest> captor = ArgumentCaptor.forClass(MpOrderRequest.class);
                verify(mercadoPagoClient).createOrder(captor.capture(), eq("daviarena"), eq(5L), any());
                MpOrderRequest sentReq = captor.getValue();

                // 1. Verificación de datos de pagador provenientes del SSO
                assertNotNull(sentReq.getPayer(), "Payer no debe ser nulo");
                assertNotNull(sentReq.getPayer().getPhone(), "payer.phone mapeado desde el SSO");
                assertEquals("57", sentReq.getPayer().getPhone().getAreaCode());
                assertEquals("3109876543", sentReq.getPayer().getPhone().getNumber());

                assertNotNull(sentReq.getPayer().getAddress(), "payer.address mapeado desde el SSO");
                assertEquals("Cra 15", sentReq.getPayer().getAddress().getStreetName());
                assertEquals("93-60", sentReq.getPayer().getAddress().getStreetNumber());
                assertEquals("110111", sentReq.getPayer().getAddress().getZipCode());
                assertEquals("Bogotá", sentReq.getPayer().getAddress().getCity());
                assertEquals("Bogotá", sentReq.getPayer().getAddress().getNeighborhood());

                // 2. Verificación Datos de Industria sugeridos (additional_info) para Checkout
                // Pro Orders API
                assertNotNull(sentReq.getAdditionalInfo(), "additional_info no debe ser nulo");
                var addInfo = sentReq.getAdditionalInfo();
                assertNotNull(addInfo.get("payer.registration_date"),
                                "additional_info['payer.registration_date'] debe provenir de Auth / SSO");
                assertTrue(addInfo.get("payer.registration_date").toString().contains("2023-06-15"));
                assertEquals(Boolean.TRUE, addInfo.get("payer.is_prime_user"),
                                "additional_info['payer.is_prime_user'] debe ser true al estar registrado en la plataforma");
                assertEquals(Boolean.TRUE, addInfo.get("payer.is_first_purchase_online"),
                                "additional_info['payer.is_first_purchase_online'] debe ser true al no tener compras previas");
                assertEquals("WEB", addInfo.get("payer.authentication_type"),
                                "additional_info['payer.authentication_type'] normalizado a enum soportado por Mercado Pago (WEB o MOBILE)");

                // 3. Verificación de capture_mode sólo con automatic
                assertEquals("automatic", sentReq.getCaptureMode());

                // 4. Verificación de statement_descriptor
                assertNotNull(sentReq.getConfig(), "Config no debe ser nulo");
                assertEquals("BiteUp DAVIarena", sentReq.getConfig().getStatementDescriptor());
        }

        @Test
        @DisplayName("initiateOrder duplica street_name a street_number y city a neighborhood si faltan, y fija capture_mode en automatic")
        void initiateOrder_DuplicaStreetNumberYNeighborhood() {
                InitPaymentRequest request = new InitPaymentRequest();
                request.setPedidoId(103L);
                request.setMonto(new BigDecimal("15000.00"));
                request.setTipoPagoId(1);

                InitPaymentRequest.PayerDto payer = new InitPaymentRequest.PayerDto();
                payer.setEmail("ana@example.com");
                payer.setFirstName("Ana");
                InitPaymentRequest.AddressDto addr = new InitPaymentRequest.AddressDto();
                addr.setStreetName("Calle 100");
                addr.setCityName("Medellin");
                payer.setAddress(addr);
                request.setPayer(payer);

                when(pedidoPagoRepository.save(any(PedidoPago.class)))
                                .thenAnswer(invocation -> invocation.getArgument(0));
                when(pedidoPagoRepository.findByUsuarioId(anyLong())).thenReturn(Collections.emptyList());

                MpOrderResponse mockResp = new MpOrderResponse();
                mockResp.setId("MP-ORD-TEST-789");
                when(mercadoPagoClient.createOrder(any(MpOrderRequest.class), any(), any(), any()))
                                .thenReturn(mockResp);

                mercadoPagoService.initiateOrder(request, "1", 20L);

                ArgumentCaptor<MpOrderRequest> captor = ArgumentCaptor.forClass(MpOrderRequest.class);
                verify(mercadoPagoClient, atLeastOnce()).createOrder(captor.capture(), eq("1"), eq(20L), any());
                MpOrderRequest sentReq = captor.getValue();

                assertEquals("automatic", sentReq.getCaptureMode());
                assertNotNull(sentReq.getPayer().getAddress());
                assertEquals("Calle 100", sentReq.getPayer().getAddress().getStreetName());
                assertEquals("Calle 100", sentReq.getPayer().getAddress().getStreetNumber());
                assertEquals("Medellin", sentReq.getPayer().getAddress().getCity());
                assertEquals("Medellin", sentReq.getPayer().getAddress().getNeighborhood());
        }

        @Test
        @DisplayName("initiateOrder envía statement_descriptor concatenando BiteUp DAVIarena aun con arenaId numérico 1")
        void initiateOrder_StatementDescriptor_FallbackArenaNumerica() {
                InitPaymentRequest request = new InitPaymentRequest();
                request.setPedidoId(102L);
                request.setMonto(new BigDecimal("25000.00"));
                request.setTipoPagoId(1);

                when(pedidoPagoRepository.save(any(PedidoPago.class)))
                                .thenAnswer(invocation -> invocation.getArgument(0));
                when(pedidoPagoRepository.findByUsuarioId(anyLong())).thenReturn(Collections.emptyList());

                MpOrderResponse mockResp = new MpOrderResponse();
                mockResp.setId("MP-ORD-TEST-456");
                when(mercadoPagoClient.createOrder(any(MpOrderRequest.class), any(), any(), any()))
                                .thenReturn(mockResp);

                mercadoPagoService.initiateOrder(request, "1", 10L);

                ArgumentCaptor<MpOrderRequest> captor = ArgumentCaptor.forClass(MpOrderRequest.class);
                verify(mercadoPagoClient, atLeastOnce()).createOrder(captor.capture(), eq("1"), eq(10L), any());
                MpOrderRequest sentReq = captor.getValue();

                assertNotNull(sentReq.getConfig());
                assertEquals("BiteUp DAVIarena", sentReq.getConfig().getStatementDescriptor());
        }

        @Test
        @DisplayName("initiateOrder recorta y sanitiza street_number cuando supera los 10 caracteres")
        void initiateOrder_SanitizaStreetNumber_CuandoSupera10Caracteres() {
                InitPaymentRequest request = new InitPaymentRequest();
                request.setPedidoId(104L);
                request.setMonto(new BigDecimal("15000.00"));
                request.setTipoPagoId(1);

                InitPaymentRequest.PayerDto payer = new InitPaymentRequest.PayerDto();
                payer.setEmail("ana@example.com");
                payer.setFirstName("Ana");
                InitPaymentRequest.AddressDto addr = new InitPaymentRequest.AddressDto();
                addr.setStreetName("Carrera 15 # 93-60 Torre B Apartamento 402");
                addr.setStreetNumber("Carrera 15 # 93-60 Torre B Apartamento 402");
                addr.setZipCode("110111");
                addr.setCityName("Bogota");
                payer.setAddress(addr);
                request.setPayer(payer);

                when(pedidoPagoRepository.save(any(PedidoPago.class)))
                                .thenAnswer(invocation -> invocation.getArgument(0));
                when(pedidoPagoRepository.findByUsuarioId(anyLong())).thenReturn(Collections.emptyList());

                MpOrderResponse mockResp = new MpOrderResponse();
                mockResp.setId("MP-ORD-TEST-999");
                when(mercadoPagoClient.createOrder(any(MpOrderRequest.class), any(), any(), any()))
                                .thenReturn(mockResp);

                mercadoPagoService.initiateOrder(request, "1", 20L);

                ArgumentCaptor<MpOrderRequest> captor = ArgumentCaptor.forClass(MpOrderRequest.class);
                verify(mercadoPagoClient, atLeastOnce()).createOrder(captor.capture(), eq("1"), eq(20L), any());
                MpOrderRequest sentReq = captor.getValue();

                assertNotNull(sentReq.getPayer().getAddress());
                assertNotNull(sentReq.getPayer().getAddress().getStreetNumber());
                assertTrue(sentReq.getPayer().getAddress().getStreetNumber().length() <= 10);
                assertEquals("93-60", sentReq.getPayer().getAddress().getStreetNumber());
        }
}
