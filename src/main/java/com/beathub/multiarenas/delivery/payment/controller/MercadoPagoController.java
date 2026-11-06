package com.beathub.multiarenas.delivery.payment.controller;

import com.beathub.multiarenas.delivery.payment.dto.request.*;
import com.beathub.multiarenas.delivery.payment.dto.response.*;
import com.beathub.multiarenas.delivery.payment.security.SecurityUtils;
import com.beathub.multiarenas.delivery.payment.service.MercadoPagoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/transacciones/mercadopago")
@RequiredArgsConstructor
@Tag(name = "Mercado Pago ApiOrders", description = "Endpoints directos de la integración oficial con la API de Orders de Mercado Pago")
public class MercadoPagoController {

    private final MercadoPagoService mercadoPagoService;

    @PostMapping("/iniciar")
    @Operation(summary = "Iniciar orden de pago en Mercado Pago", description = "Crea orden en v1/orders de Mercado Pago y retorna init_point / URL de checkout")
    public ResponseEntity<ApiResponse<PaymentInitResponse>> initiateOrder(
            @Valid @RequestBody InitPaymentRequest request,
            HttpServletRequest httpRequest
    ) {
        String arenaId = resolveArenaId(httpRequest);
        Long usuarioId = resolveUsuarioId(httpRequest);

        PaymentInitResponse response = mercadoPagoService.initiateOrder(request, arenaId, usuarioId);
        return ResponseEntity.ok(ApiResponse.ok(response, "Orden de Mercado Pago generada exitosamente"));
    }

    @GetMapping("/{pedidoPagoId}/estado")
    @Operation(summary = "Consultar estado de orden MP por ID local", description = "Sincroniza y retorna el estado de la orden ante Mercado Pago")
    public ResponseEntity<ApiResponse<PaymentStatusResponse>> getStatus(
            @PathVariable Long pedidoPagoId,
            HttpServletRequest httpRequest
    ) {
        String arenaId = resolveArenaId(httpRequest);
        Long usuarioId = resolveUsuarioId(httpRequest);

        PaymentStatusResponse response = mercadoPagoService.queryOrderStatus(pedidoPagoId, arenaId, usuarioId);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @GetMapping("/orden/{orderId}/estado")
    @Operation(summary = "Consultar estado de orden por MP Order ID", description = "Consulta directamente la orden en Mercado Pago con su Order ID")
    public ResponseEntity<ApiResponse<PaymentStatusResponse>> getStatusByOrderId(
            @PathVariable String orderId,
            HttpServletRequest httpRequest
    ) {
        String arenaId = resolveArenaId(httpRequest);
        Long usuarioId = resolveUsuarioId(httpRequest);

        PaymentStatusResponse response = mercadoPagoService.queryOrderByMpOrderId(orderId, arenaId, usuarioId);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @GetMapping("/referencia/{referenciaPago}/estado")
    @Operation(summary = "Consultar estado de orden por Referencia de Pago", description = "Consulta por referencia única (MP-ORD-...)")
    public ResponseEntity<ApiResponse<PaymentStatusResponse>> getStatusByReference(
            @PathVariable String referenciaPago,
            HttpServletRequest httpRequest
    ) {
        String arenaId = resolveArenaId(httpRequest);
        Long usuarioId = resolveUsuarioId(httpRequest);

        PaymentStatusResponse response = mercadoPagoService.queryOrderByReference(referenciaPago, arenaId, usuarioId);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @GetMapping("/pedido/{pedidoId}/estado")
    @Operation(summary = "Consultar estado por Pedido ID", description = "Sincroniza el pago más reciente del pedido")
    public ResponseEntity<ApiResponse<PaymentStatusResponse>> getStatusByPedidoId(
            @PathVariable Long pedidoId,
            HttpServletRequest httpRequest
    ) {
        String arenaId = resolveArenaId(httpRequest);
        Long usuarioId = resolveUsuarioId(httpRequest);

        PaymentStatusResponse response = mercadoPagoService.queryOrderByPedidoId(pedidoId, arenaId, usuarioId);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @PostMapping("/pagar-token")
    @Operation(summary = "Cobro One-Click / Recurrente con tarjeta guardada en Mercado Pago", description = "Ejecuta pago con perfil recurrente en v1/orders")
    public ResponseEntity<ApiResponse<PaymentTokenResponse>> payWithToken(
            @Valid @RequestBody PayWithTokenRequest request,
            HttpServletRequest httpRequest
    ) {
        String arenaId = resolveArenaId(httpRequest);
        Long usuarioId = resolveUsuarioId(httpRequest);
        String ipCliente = httpRequest.getRemoteAddr();

        PaymentTokenResponse response = mercadoPagoService.payWithToken(request, arenaId, usuarioId, ipCliente);
        return ResponseEntity.ok(ApiResponse.ok(response, response.getMensaje()));
    }

    @PostMapping("/reembolso")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN_ARENA')")
    @Operation(summary = "Reembolso de orden en Mercado Pago", description = "Ejecuta v1/orders/{orderId}/refund")
    public ResponseEntity<ApiResponse<RefundResponse>> refundOrder(
            @Valid @RequestBody RefundPaymentRequest request,
            HttpServletRequest httpRequest
    ) {
        String arenaId = resolveArenaId(httpRequest);
        Long usuarioId = resolveUsuarioId(httpRequest);

        RefundResponse response = mercadoPagoService.refundOrder(request, arenaId, usuarioId);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @PostMapping("/tarjetas/tokenizar")
    @Operation(summary = "Tokenizar y vincular tarjeta en Mercado Pago", description = "Crea token y vincula tarjeta al Customer en Mercado Pago")
    public ResponseEntity<ApiResponse<TarjetaGuardadaResponse>> tokenizeCard(
            @Valid @RequestBody TokenizarTarjetaRequest request,
            HttpServletRequest httpRequest
    ) {
        String arenaId = resolveArenaId(httpRequest);
        Long usuarioId = resolveUsuarioId(httpRequest);
        String ipCliente = httpRequest.getRemoteAddr();

        TarjetaGuardadaResponse response = mercadoPagoService.tokenizeAndSaveCard(request, arenaId, usuarioId, ipCliente);
        return ResponseEntity.ok(ApiResponse.ok(response, response.getMensaje()));
    }

    @GetMapping("/tarjetas/mis-tarjetas")
    @Operation(summary = "Listar tarjetas de Mercado Pago del usuario", description = "Retorna tarjetas guardadas para pagos One-Click")
    public ResponseEntity<ApiResponse<List<TarjetaGuardadaResponse>>> getMyCards(
            HttpServletRequest httpRequest
    ) {
        String arenaId = resolveArenaId(httpRequest);
        Long usuarioId = resolveUsuarioId(httpRequest);

        List<TarjetaGuardadaResponse> tarjetas = mercadoPagoService.getUserCards(arenaId, usuarioId);
        return ResponseEntity.ok(ApiResponse.ok(tarjetas));
    }

    @DeleteMapping("/tarjetas/{tarjetaId}")
    @Operation(summary = "Desvincular tarjeta de Mercado Pago", description = "Desactiva tarjeta del perfil")
    public ResponseEntity<ApiResponse<Void>> deleteCard(
            @PathVariable Long tarjetaId,
            HttpServletRequest httpRequest
    ) {
        String arenaId = resolveArenaId(httpRequest);
        Long usuarioId = resolveUsuarioId(httpRequest);

        mercadoPagoService.deleteUserCard(tarjetaId, arenaId, usuarioId);
        return ResponseEntity.ok(ApiResponse.ok(null, "Tarjeta desvinculada exitosamente de Mercado Pago"));
    }

    private String resolveArenaId(HttpServletRequest request) {
        Object attr = request.getAttribute("X-Arena-Id");
        if (attr != null) return attr.toString();
        return SecurityUtils.getCurrentArenaId().map(String::valueOf).orElse(request.getHeader("X-Arena-Id"));
    }

    private Long resolveUsuarioId(HttpServletRequest request) {
        Object attr = request.getAttribute("X-User-Id");
        if (attr instanceof Long id) return id;
        if (attr instanceof Number num) return num.longValue();
        return SecurityUtils.getCurrentUsuarioId().orElse(1L);
    }
}

