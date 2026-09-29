package com.beathub.multiarenas.delivery.payment.controller;

import com.beathub.multiarenas.delivery.payment.dto.request.InitPaymentRequest;
import com.beathub.multiarenas.delivery.payment.dto.request.RefundPaymentRequest;
import com.beathub.multiarenas.delivery.payment.dto.request.VerifyCardRequest;
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
