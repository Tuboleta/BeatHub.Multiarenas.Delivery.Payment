package com.beathub.multiarenas.delivery.payment.service;

import com.beathub.multiarenas.delivery.payment.client.OrderingServiceClient;
import com.beathub.multiarenas.delivery.payment.client.dto.PedidoClientResponse;
import com.beathub.multiarenas.delivery.payment.dto.request.CrearGrupoPagoRequest;
import com.beathub.multiarenas.delivery.payment.dto.request.PagarCuotaGrupoRequest;
import com.beathub.multiarenas.delivery.payment.dto.request.RecalcularCuotasRequest;
import com.beathub.multiarenas.delivery.payment.dto.request.UnirseGrupoRequest;
import com.beathub.multiarenas.delivery.payment.dto.response.GrupoPagoResponse;
import com.beathub.multiarenas.delivery.payment.dto.response.PaymentInitResponse;
import com.beathub.multiarenas.delivery.payment.entity.GrupoPago;
import com.beathub.multiarenas.delivery.payment.entity.GrupoPagoUsuario;
import com.beathub.multiarenas.delivery.payment.entity.TipoGrupoPago;
import com.beathub.multiarenas.delivery.payment.exception.BadRequestException;
import com.beathub.multiarenas.delivery.payment.repository.GrupoPagoRepository;
import com.beathub.multiarenas.delivery.payment.repository.GrupoPagoUsuarioRepository;
import com.beathub.multiarenas.delivery.payment.repository.PedidoPagoRepository;
import com.beathub.multiarenas.delivery.payment.repository.TipoGrupoPagoRepository;
import com.beathub.multiarenas.delivery.payment.service.impl.GrupoPagoServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GrupoPagoServiceTest {

    @Mock
    private GrupoPagoRepository grupoPagoRepository;

    @Mock
    private GrupoPagoUsuarioRepository grupoPagoUsuarioRepository;

    @Mock
    private TipoGrupoPagoRepository tipoGrupoPagoRepository;

    @Mock
    private PedidoPagoRepository pedidoPagoRepository;

    @Mock
    private OrderingServiceClient orderingClient;

    @Mock
    private PaymentService paymentService;

    @Mock
    private QrCodeGeneratorService qrCodeGeneratorService;

    @InjectMocks
    private GrupoPagoServiceImpl grupoPagoService;

    private TipoGrupoPago tipoGrupoPago;
    private GrupoPago grupoPago;
    private GrupoPagoUsuario grupoPagoUsuario;

    @BeforeEach
    void setUp() {
        tipoGrupoPago = TipoGrupoPago.builder()
                .id(1)
                .nombre("TEMPORAL_DIA")
                .descripcion("Temporal")
                .estadoId(1)
                .build();

        grupoPago = GrupoPago.builder()
                .id(100L)
                .codigoUnico("VACA-TEST01")
                .nombre("Vaca Palco 10")
                .arenaId("ARENA-BOG-01")
                .pedidoId(55L)
                .tipoGrupoPago(tipoGrupoPago)
                .esPermanente(false)
                .modalidadDivision("POR_PARTES_IGUALES")
                .montoPreasignado(new BigDecimal("25000.00"))
                .cantidadPersonas(4)
                .fechaExpiracion(LocalDateTime.now().plusHours(4))
                .valorTotal(new BigDecimal("100000.00"))
                .totalPagado(BigDecimal.ZERO)
                .saldoPendiente(new BigDecimal("100000.00"))
                .estadoId(1)
                .creacionUsuario(10L)
                .creacionFecha(LocalDateTime.now())
                .build();

        grupoPagoUsuario = GrupoPagoUsuario.builder()
                .id(1L)
                .grupoPago(grupoPago)
                .usuarioId(10L)
                .montoAsignado(new BigDecimal("25000.00"))
                .montoPagado(BigDecimal.ZERO)
                .porcentaje(new BigDecimal("25.00"))
                .esLider(true)
                .estadoId(1)
                .creacionUsuario(10L)
                .creacionFecha(LocalDateTime.now())
                .build();
    }

    @Test
    @DisplayName("Debe crear un grupo de pago (Vaca) en partes iguales con cuota preasignada y QR")
    void crearGrupo_PartesIguales_CalculaCuotaPreasignadaYQr() {
        CrearGrupoPagoRequest request = CrearGrupoPagoRequest.builder()
                .pedidoId(55L)
                .nombre("Vaca Palco 10")
                .divisionTipo("POR_PARTES_IGUALES")
                .cantidadPersonas(4)
                .build();

        PedidoClientResponse pedidoResponse = PedidoClientResponse.builder()
                .id(55L)
                .usuarioId(10L)
                .arenaId("ARENA-BOG-01")
                .valorTotal(new BigDecimal("100000.00"))
                .estadoId(1)
                .build();

        when(grupoPagoRepository.findByPedidoIdAndEstadoId(55L, 1)).thenReturn(Optional.empty());
        when(orderingClient.getPedido(eq(55L), any())).thenReturn(Optional.of(pedidoResponse));
        when(tipoGrupoPagoRepository.findById(anyInt())).thenReturn(Optional.of(tipoGrupoPago));
        when(grupoPagoRepository.findByCodigoUnico(anyString())).thenReturn(Optional.empty());
        when(grupoPagoRepository.save(any(GrupoPago.class))).thenAnswer(inv -> {
            GrupoPago gp = inv.getArgument(0);
            gp.setId(100L);
            return gp;
        });
        when(grupoPagoUsuarioRepository.findByGrupoPagoId(anyLong())).thenReturn(List.of(grupoPagoUsuario));
        when(pedidoPagoRepository.findByGrupoPagoUsuario_GrupoPago_Id(anyLong())).thenReturn(Collections.emptyList());
        when(qrCodeGeneratorService.generateQrCodeBase64(anyString(), anyInt(), anyInt())).thenReturn("data:image/png;base64,mockQrCode");

        GrupoPagoResponse response = grupoPagoService.crearGrupo(request, "ARENA-BOG-01", 10L, "mock-token");

        assertNotNull(response);
        assertEquals(100L, response.getId());
        assertTrue(response.getCodigoUnico().startsWith("VACA-"));
        assertEquals("POR_PARTES_IGUALES", response.getModalidadDivision());
        assertEquals(new BigDecimal("25000.00"), response.getMontoPreasignado());
        assertEquals(4, response.getCantidadPersonas());
        assertEquals("data:image/png;base64,mockQrCode", response.getQrCodeImage());
        assertNotNull(response.getQrCodeData());

        verify(orderingClient).asociarGrupoPago(eq(55L), eq(100L), any());
    }

    @Test
    @DisplayName("Debe crear un grupo en modalidad LIBRE_PAGO con rango dinámico y sin cuota preasignada fija")
    void crearGrupo_LibrePago_SinCuotaPreasignadaYQr() {
        CrearGrupoPagoRequest request = CrearGrupoPagoRequest.builder()
                .pedidoId(55L)
                .nombre("Vaca Libre Palco")
                .divisionTipo("LIBRE_PAGO")
                .valorTotal(new BigDecimal("80000.00"))
                .build();

        when(grupoPagoRepository.findByPedidoIdAndEstadoId(55L, 1)).thenReturn(Optional.empty());
        when(orderingClient.getPedido(eq(55L), any())).thenReturn(Optional.empty());
        when(tipoGrupoPagoRepository.findById(anyInt())).thenReturn(Optional.of(tipoGrupoPago));
        when(grupoPagoRepository.findByCodigoUnico(anyString())).thenReturn(Optional.empty());
        when(grupoPagoRepository.save(any(GrupoPago.class))).thenAnswer(inv -> {
            GrupoPago gp = inv.getArgument(0);
            gp.setId(101L);
            return gp;
        });
        when(grupoPagoUsuarioRepository.findByGrupoPagoId(anyLong())).thenReturn(List.of(grupoPagoUsuario));
        when(pedidoPagoRepository.findByGrupoPagoUsuario_GrupoPago_Id(anyLong())).thenReturn(Collections.emptyList());
        when(qrCodeGeneratorService.generateQrCodeBase64(anyString(), anyInt(), anyInt())).thenReturn("data:image/png;base64,mockQrCode");

        GrupoPagoResponse response = grupoPagoService.crearGrupo(request, "ARENA-BOG-01", 10L, "mock-token");

        assertNotNull(response);
        assertEquals("LIBRE_PAGO", response.getModalidadDivision());
        assertNull(response.getMontoPreasignado());
        assertEquals(new BigDecimal("0.01"), response.getRangoAporteMinimo());
        assertEquals(new BigDecimal("80000.00"), response.getRangoAporteMaximo());
        assertEquals("data:image/png;base64,mockQrCode", response.getQrCodeImage());
    }

    @Test
    @DisplayName("Debe permitir reutilizar un grupo permanente para un nuevo pedido")
    void crearGrupo_UsandoGrupoPermanente_Exitoso() {
        GrupoPago grupoPermanente = GrupoPago.builder()
                .id(200L)
                .codigoUnico("VACA-PALCO-VIP")
                .nombre("Palco VIP Frecuente")
                .arenaId("ARENA-BOG-01")
                .esPermanente(true)
                .tipoGrupoPago(tipoGrupoPago)
                .totalPagado(BigDecimal.ZERO)
                .saldoPendiente(BigDecimal.ZERO)
                .valorTotal(BigDecimal.ZERO)
                .estadoId(1)
                .build();

        CrearGrupoPagoRequest request = CrearGrupoPagoRequest.builder()
                .pedidoId(60L)
                .grupoPagoPermanenteId(200L)
                .divisionTipo("POR_PARTES_IGUALES")
                .cantidadPersonas(2)
                .valorTotal(new BigDecimal("60000.00"))
                .build();

        when(grupoPagoRepository.findByPedidoIdAndEstadoId(60L, 1)).thenReturn(Optional.empty());
        when(orderingClient.getPedido(eq(60L), any())).thenReturn(Optional.empty());
        when(grupoPagoRepository.findById(200L)).thenReturn(Optional.of(grupoPermanente));
        when(grupoPagoRepository.save(any(GrupoPago.class))).thenAnswer(inv -> inv.getArgument(0));
        when(grupoPagoUsuarioRepository.findByGrupoPagoId(200L)).thenReturn(List.of(grupoPagoUsuario));
        when(pedidoPagoRepository.findByGrupoPagoUsuario_GrupoPago_Id(200L)).thenReturn(Collections.emptyList());

        GrupoPagoResponse response = grupoPagoService.crearGrupo(request, "ARENA-BOG-01", 10L, "mock-token");

        assertNotNull(response);
        assertEquals("VACA-PALCO-VIP", response.getCodigoUnico());
        assertEquals(new BigDecimal("60000.00"), response.getValorTotal());
        assertEquals(new BigDecimal("30000.00"), response.getMontoPreasignado());
        verify(orderingClient).asociarGrupoPago(eq(60L), eq(200L), any());
    }

    @Test
    @DisplayName("Debe fallar al crear grupo si ya existe un grupo de pago activo para el mismo pedido")
    void crearGrupo_Falla_SiYaExisteActivo() {
        CrearGrupoPagoRequest request = CrearGrupoPagoRequest.builder()
                .pedidoId(55L)
                .build();

        when(grupoPagoRepository.findByPedidoIdAndEstadoId(55L, 1)).thenReturn(Optional.of(grupoPago));

        assertThrows(BadRequestException.class, () ->
                grupoPagoService.crearGrupo(request, "ARENA-BOG-01", 10L, null));

        verify(grupoPagoRepository, never()).save(any());
    }

    @Test
    @DisplayName("Debe consultar grupo de pago por código único")
    void obtenerPorCodigo_Exitoso() {
        when(grupoPagoRepository.findByCodigoUnico("VACA-TEST01")).thenReturn(Optional.of(grupoPago));
        when(grupoPagoUsuarioRepository.findByGrupoPagoId(100L)).thenReturn(List.of(grupoPagoUsuario));
        when(pedidoPagoRepository.findByGrupoPagoUsuario_GrupoPago_Id(100L)).thenReturn(Collections.emptyList());

        GrupoPagoResponse response = grupoPagoService.obtenerPorCodigo("VACA-TEST01", 10L);

        assertNotNull(response);
        assertEquals("VACA-TEST01", response.getCodigoUnico());
        assertEquals(1, response.getParticipantes().size());
        assertTrue(response.getEsLider());
    }

    @Test
    @DisplayName("Debe permitir que un usuario se una a un grupo de pago existente")
    void unirseAGrupo_Exitoso() {
        UnirseGrupoRequest request = UnirseGrupoRequest.builder()
                .montoAsignado(new BigDecimal("25000.00"))
                .build();

        GrupoPagoUsuario nuevoMiembro = GrupoPagoUsuario.builder()
                .id(2L)
                .grupoPago(grupoPago)
                .usuarioId(20L)
                .montoAsignado(new BigDecimal("25000.00"))
                .montoPagado(BigDecimal.ZERO)
                .esLider(false)
                .estadoId(1)
                .build();

        when(grupoPagoRepository.findByCodigoUnico("VACA-TEST01")).thenReturn(Optional.of(grupoPago));
        when(grupoPagoUsuarioRepository.findByGrupoPagoIdAndUsuarioId(100L, 20L)).thenReturn(Optional.empty());
        when(grupoPagoUsuarioRepository.findByGrupoPagoId(100L)).thenReturn(List.of(grupoPagoUsuario, nuevoMiembro));
        when(pedidoPagoRepository.findByGrupoPagoUsuario_GrupoPago_Id(100L)).thenReturn(Collections.emptyList());

        GrupoPagoResponse response = grupoPagoService.unirseAGrupo("VACA-TEST01", request, 20L);

        assertNotNull(response);
        verify(grupoPagoUsuarioRepository).save(any(GrupoPagoUsuario.class));
    }

    @Test
    @DisplayName("Debe recalcular cuotas equitativamente entre los participantes activos")
    void recalcularCuotas_Exitoso() {
        GrupoPagoUsuario u1 = GrupoPagoUsuario.builder().id(1L).grupoPago(grupoPago).usuarioId(10L).esLider(true).estadoId(1).build();
        GrupoPagoUsuario u2 = GrupoPagoUsuario.builder().id(2L).grupoPago(grupoPago).usuarioId(20L).esLider(false).estadoId(1).build();

        when(grupoPagoRepository.findById(100L)).thenReturn(Optional.of(grupoPago));
        when(grupoPagoUsuarioRepository.findByGrupoPagoIdAndEstadoIdNot(100L, 3)).thenReturn(List.of(u1, u2));
        when(grupoPagoUsuarioRepository.findByGrupoPagoId(100L)).thenReturn(List.of(u1, u2));
        when(pedidoPagoRepository.findByGrupoPagoUsuario_GrupoPago_Id(100L)).thenReturn(Collections.emptyList());
        when(grupoPagoRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        RecalcularCuotasRequest request = RecalcularCuotasRequest.builder().build();
        GrupoPagoResponse response = grupoPagoService.recalcularCuotas(100L, request, 10L);

        assertNotNull(response);
        assertEquals(new BigDecimal("50000.00"), u1.getMontoAsignado());
        assertEquals(new BigDecimal("50000.00"), u2.getMontoAsignado());
        verify(grupoPagoUsuarioRepository, times(2)).save(any(GrupoPagoUsuario.class));
    }

    @Test
    @DisplayName("Debe pagar cuota preasignada cuando no se envía monto explícito en partes iguales")
    void pagarCuotaGrupo_PartesIguales_UsaCuotaPreasignada() {
        PagarCuotaGrupoRequest request = PagarCuotaGrupoRequest.builder()
                .returnUrl("https://front.beathub.com/pago/exito")
                .failUrl("https://front.beathub.com/pago/fallo")
                .build();

        PaymentInitResponse mockPaymentResp = PaymentInitResponse.builder()
                .pedidoPagoId(777L)
                .pedidoId(55L)
                .referenciaPago("PAY-12345")
                .formUrl("https://credibanco.com/checkout/form")
                .monto(new BigDecimal("25000.00"))
                .build();

        when(grupoPagoRepository.findById(100L)).thenReturn(Optional.of(grupoPago));
        when(grupoPagoUsuarioRepository.findByGrupoPagoIdAndUsuarioId(100L, 10L)).thenReturn(Optional.of(grupoPagoUsuario));
        when(paymentService.initiatePayment(any(), any(), eq(10L))).thenReturn(mockPaymentResp);

        PaymentInitResponse response = grupoPagoService.pagarCuotaGrupo(100L, request, "ARENA-BOG-01", 10L);

        assertNotNull(response);
        assertEquals("PAY-12345", response.getReferenciaPago());
        verify(paymentService).initiatePayment(argThat(req -> req.getMonto().compareTo(new BigDecimal("25000.00")) == 0), any(), eq(10L));
    }

    @Test
    @DisplayName("Debe exigir monto en rango y fallar si excede saldo pendiente en LIBRE_PAGO")
    void pagarCuotaGrupo_LibrePago_ValidaMontoEnRango() {
        grupoPago.setModalidadDivision("LIBRE_PAGO");
        grupoPago.setMontoPreasignado(null);

        // Caso 1: Falla sin monto
        PagarCuotaGrupoRequest reqSinMonto = PagarCuotaGrupoRequest.builder().build();
        when(grupoPagoRepository.findById(100L)).thenReturn(Optional.of(grupoPago));
        when(grupoPagoUsuarioRepository.findByGrupoPagoIdAndUsuarioId(100L, 10L)).thenReturn(Optional.of(grupoPagoUsuario));

        assertThrows(BadRequestException.class, () ->
                grupoPagoService.pagarCuotaGrupo(100L, reqSinMonto, "ARENA-BOG-01", 10L));

        // Caso 2: Falla si excede saldo pendiente
        PagarCuotaGrupoRequest reqExcede = PagarCuotaGrupoRequest.builder()
                .monto(new BigDecimal("150000.00"))
                .build();

        assertThrows(BadRequestException.class, () ->
                grupoPagoService.pagarCuotaGrupo(100L, reqExcede, "ARENA-BOG-01", 10L));
    }

    @Test
    @DisplayName("Debe cancelar un grupo de pago si no tiene aportes recaudados")
    void cancelarGrupo_Exitoso() {
        when(grupoPagoRepository.findById(100L)).thenReturn(Optional.of(grupoPago));
        when(grupoPagoRepository.save(any(GrupoPago.class))).thenAnswer(inv -> inv.getArgument(0));
        when(grupoPagoUsuarioRepository.findByGrupoPagoId(100L)).thenReturn(List.of(grupoPagoUsuario));
        when(pedidoPagoRepository.findByGrupoPagoUsuario_GrupoPago_Id(100L)).thenReturn(Collections.emptyList());

        GrupoPagoResponse response = grupoPagoService.cancelarGrupo(100L, 10L);

        assertNotNull(response);
        assertEquals("CANCELADO", response.getEstadoNombre());
    }

    @Test
    @DisplayName("Debe fallar al cancelar un grupo si ya tiene aportes recaudados")
    void cancelarGrupo_Falla_SiTieneAportes() {
        grupoPago.setTotalPagado(new BigDecimal("25000.00"));

        when(grupoPagoRepository.findById(100L)).thenReturn(Optional.of(grupoPago));

        assertThrows(BadRequestException.class, () ->
                grupoPagoService.cancelarGrupo(100L, 10L));

        verify(grupoPagoRepository, never()).save(any());
    }
}
