package com.beathub.multiarenas.delivery.payment.controller;

import com.beathub.multiarenas.delivery.payment.dto.request.*;
import com.beathub.multiarenas.delivery.payment.dto.response.*;
import com.beathub.multiarenas.delivery.payment.service.PaymentService;
import com.beathub.multiarenas.delivery.payment.security.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/transacciones")
@RequiredArgsConstructor
@Tag(name = "Pagos y Transacciones", description = "Endpoints para inicialización, consulta y gestión de pagos con Credibanco")
public class PaymentController {

    private final PaymentService paymentService;

    @PostMapping("/iniciar")
    @Operation(summary = "Iniciar flujo de pago", description = "Registra la orden en el microservicio y genera la URL de checkout de Credibanco (register.do)")
    public ResponseEntity<ApiResponse<PaymentInitResponse>> initiatePayment(
            @Valid @RequestBody InitPaymentRequest request,
            HttpServletRequest httpRequest
    ) {
        String arenaId = resolveArenaId(httpRequest);
        Long usuarioId = resolveUsuarioId(httpRequest);

        PaymentInitResponse response = paymentService.initiatePayment(request, arenaId, usuarioId);
        return ResponseEntity.ok(ApiResponse.ok(response, "Orden de pago generada exitosamente"));
    }

    @GetMapping("/{pedidoPagoId}/estado")
    @Operation(summary = "Consultar estado de pago por ID", description = "Consulta y sincroniza el estado extendido de la transacción con Credibanco")
    public ResponseEntity<ApiResponse<PaymentStatusResponse>> getStatus(
            @PathVariable Long pedidoPagoId,
            HttpServletRequest httpRequest
    ) {
        String arenaId = resolveArenaId(httpRequest);
        Long usuarioId = resolveUsuarioId(httpRequest);

        PaymentStatusResponse response = paymentService.queryPaymentStatus(pedidoPagoId, arenaId, usuarioId);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @GetMapping("/referencia/{referenciaPago}/estado")
    @Operation(summary = "Consultar estado de pago por Referencia", description = "Consulta el estado mediante la referencia generada (PAY-xxx)")
    public ResponseEntity<ApiResponse<PaymentStatusResponse>> getStatusByReference(
            @PathVariable String referenciaPago,
            HttpServletRequest httpRequest
    ) {
        String arenaId = resolveArenaId(httpRequest);
        Long usuarioId = resolveUsuarioId(httpRequest);

        PaymentStatusResponse response = paymentService.queryPaymentStatusByReference(referenciaPago, arenaId, usuarioId);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @PostMapping("/reembolso")
    @Operation(summary = "Solicitar anulación o reembolso", description = "Ejecuta solicitud refund.do ante Credibanco")
    public ResponseEntity<ApiResponse<RefundResponse>> refundPayment(
            @Valid @RequestBody RefundPaymentRequest request,
            HttpServletRequest httpRequest
    ) {
        String arenaId = resolveArenaId(httpRequest);
        Long usuarioId = resolveUsuarioId(httpRequest);

        RefundResponse response = paymentService.refundPayment(request, arenaId, usuarioId);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @PostMapping("/tarjetas/verificar")
    @Operation(summary = "Verificar tarjeta", description = "Valida estado y autenticidad de tarjeta en Credibanco (verifyCard.do)")
    public ResponseEntity<ApiResponse<VerifyCardResponse>> verifyCard(
            @Valid @RequestBody VerifyCardRequest request,
            HttpServletRequest httpRequest
    ) {
        String arenaId = resolveArenaId(httpRequest);
        Long usuarioId = resolveUsuarioId(httpRequest);

        VerifyCardResponse response = paymentService.verifyCard(request, arenaId, usuarioId);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @PostMapping("/pagar-token")
    @Operation(summary = "Pagar con tarjeta guardada (Token / One-Click)", description = "Ejecuta cobro directo utilizando el bindingId tokenizado en Credibanco sin requerir reingresar los datos de la tarjeta")
    public ResponseEntity<ApiResponse<PaymentTokenResponse>> payWithToken(
            @Valid @RequestBody PayWithTokenRequest request,
            HttpServletRequest httpRequest
    ) {
        String arenaId = resolveArenaId(httpRequest);
        Long usuarioId = resolveUsuarioId(httpRequest);
        String ipCliente = httpRequest.getRemoteAddr();

        PaymentTokenResponse response = paymentService.payWithToken(request, arenaId, usuarioId, ipCliente);
        return ResponseEntity.ok(ApiResponse.ok(response, response.getMensaje()));
    }

    @GetMapping("/tarjetas/mis-tarjetas")
    @Operation(summary = "Listar tarjetas guardadas del usuario", description = "Devuelve el listado de tarjetas activas tokenizadas del usuario autenticado")
    public ResponseEntity<ApiResponse<java.util.List<TarjetaGuardadaResponse>>> getMyCards(
            HttpServletRequest httpRequest
    ) {
        String arenaId = resolveArenaId(httpRequest);
        Long usuarioId = resolveUsuarioId(httpRequest);

        java.util.List<TarjetaGuardadaResponse> tarjetas = paymentService.getUserCards(arenaId, usuarioId);
        return ResponseEntity.ok(ApiResponse.ok(tarjetas));
    }

    @PostMapping("/tarjetas/guardar")
    @Operation(summary = "Guardar / Vincular tarjeta tokenizada", description = "Registra un token bindingId asociado al usuario para futuros cobros")
    public ResponseEntity<ApiResponse<TarjetaGuardadaResponse>> saveCard(
            @Valid @RequestBody SaveCardBindingRequest request,
            HttpServletRequest httpRequest
    ) {
        String arenaId = resolveArenaId(httpRequest);
        Long usuarioId = resolveUsuarioId(httpRequest);

        TarjetaGuardadaResponse response = paymentService.saveCardBinding(request, arenaId, usuarioId);
        return ResponseEntity.ok(ApiResponse.ok(response, "Tarjeta vinculada exitosamente"));
    }

    @DeleteMapping("/tarjetas/{tarjetaId}")
    @Operation(summary = "Eliminar / Desvincular tarjeta", description = "Desactiva la tarjeta del usuario y solicita unBindCard.do a Credibanco")
    public ResponseEntity<ApiResponse<Void>> deleteCard(
            @PathVariable Long tarjetaId,
            HttpServletRequest httpRequest
    ) {
        String arenaId = resolveArenaId(httpRequest);
        Long usuarioId = resolveUsuarioId(httpRequest);

        paymentService.deleteUserCard(tarjetaId, arenaId, usuarioId);
        return ResponseEntity.ok(ApiResponse.ok(null, "Tarjeta desvinculada exitosamente"));
    }

    @PutMapping("/tarjetas/{tarjetaId}/predeterminada")
    @Operation(summary = "Marcar tarjeta como predeterminada", description = "Establece la tarjeta seleccionada como el método preferido del cliente")
    public ResponseEntity<ApiResponse<Void>> setDefaultCard(
            @PathVariable Long tarjetaId,
            HttpServletRequest httpRequest
    ) {
        String arenaId = resolveArenaId(httpRequest);
        Long usuarioId = resolveUsuarioId(httpRequest);

        paymentService.setDefaultCard(tarjetaId, arenaId, usuarioId);
        return ResponseEntity.ok(ApiResponse.ok(null, "Tarjeta configurada como predeterminada"));
    }

    @GetMapping("/tarjetas/credibanco-bindings")
    @Operation(summary = "Sincronizar tarjetas desde Credibanco", description = "Consulta directamente las tarjetas vinculadas en Credibanco con getBindings.do")
    public ResponseEntity<ApiResponse<com.beathub.multiarenas.delivery.payment.client.dto.CredibancoGetBindingsResponse>> syncBindings(
            HttpServletRequest httpRequest
    ) {
        String arenaId = resolveArenaId(httpRequest);
        Long usuarioId = resolveUsuarioId(httpRequest);

        var response = paymentService.syncCredibancoBindings(arenaId, usuarioId);
        return ResponseEntity.ok(ApiResponse.ok(response));
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
