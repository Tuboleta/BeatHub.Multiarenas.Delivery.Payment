package com.beathub.multiarenas.delivery.payment.controller;

import com.beathub.multiarenas.delivery.payment.entity.PedidoPago;
import com.beathub.multiarenas.delivery.payment.repository.PedidoPagoRepository;
import com.beathub.multiarenas.delivery.payment.service.IntegrationAuditService;
import com.beathub.multiarenas.delivery.payment.service.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/webhook/credibanco")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Credibanco Webhook", description = "Endpoint de notificación callback asíncrono para Credibanco")
public class CredibancoWebhookController {

    private final PaymentService paymentService;
    private final IntegrationAuditService auditService;
    private final PedidoPagoRepository pedidoPagoRepository;

    @GetMapping("/callback")
    @Operation(summary = "Callback GET de notificación de pago", description = "Recibe el resultado enviado por Credibanco via query params")
    public ResponseEntity<String> handleCallbackGet(
            @RequestParam(value = "mdOrder", required = false) String mdOrder,
            @RequestParam(value = "orderNumber", required = false) String orderNumber,
            @RequestParam(value = "operation", required = false) String operation,
            @RequestParam(value = "status", required = false) Integer status,
            @RequestParam(value = "checksum", required = false) String checksum,
            @RequestParam(value = "sign_alias", required = false) String signAlias,
            @RequestParam(required = false) Map<String, String> allParams
    ) {
        LocalDateTime startTime = LocalDateTime.now();
        String operacionLog = "Webhook_Credibanco_Callback_GET";

        log.info("\n" +
                "================================================================================\n" +
                "[WEBHOOK CREDIBANCO - CALLBACK GET] Notificación de pago entrante\n" +
                "   -> Operación: {}\n" +
                "   -> mdOrder: {}\n" +
                "   -> orderNumber: {}\n" +
                "   -> operation: {}\n" +
                "   -> status: {}\n" +
                "   -> checksum: {}\n" +
                "   -> signAlias: {}\n" +
                "   -> allParams: {}\n" +
                "================================================================================",
                operacionLog, mdOrder, orderNumber, operation, status, checksum, signAlias, allParams);

        Map<String, Object> payloadData = new HashMap<>();
        if (allParams != null) {
            payloadData.putAll(allParams);
        }
        payloadData.putIfAbsent("mdOrder", mdOrder);
        payloadData.putIfAbsent("orderNumber", orderNumber);
        payloadData.putIfAbsent("operation", operation);
        payloadData.putIfAbsent("status", status);
        payloadData.putIfAbsent("checksum", checksum);
        payloadData.putIfAbsent("sign_alias", signAlias);

        String arenaId = resolveArenaId(orderNumber, mdOrder);
        Long usuarioId = resolveUsuarioId(orderNumber, mdOrder);

        try {
            paymentService.processCredibancoCallback(mdOrder, orderNumber, operation, status, checksum, signAlias);

            log.info("[WEBHOOK CREDIBANCO - CALLBACK GET] Procesado con éxito: orderNumber={}, mdOrder={}", orderNumber, mdOrder);

            auditService.logWebhook(
                    IntegrationAuditService.SERVICIO_CREDIBANCO,
                    arenaId,
                    usuarioId,
                    operacionLog,
                    payloadData,
                    "OK",
                    startTime,
                    LocalDateTime.now(),
                    200,
                    "SUCCESS"
            );
            return ResponseEntity.ok("OK");
        } catch (Exception ex) {
            log.error("[WEBHOOK CREDIBANCO - CALLBACK GET] Error al procesar callback: {}", ex.getMessage(), ex);

            auditService.logWebhook(
                    IntegrationAuditService.SERVICIO_CREDIBANCO,
                    arenaId,
                    usuarioId,
                    operacionLog,
                    payloadData,
                    "ERROR: " + ex.getMessage(),
                    startTime,
                    LocalDateTime.now(),
                    500,
                    "FAILED"
            );
            throw ex;
        }
    }

    @PostMapping("/callback")
    @Operation(summary = "Callback POST de notificación de pago", description = "Recibe el resultado enviado por Credibanco via form-urlencoded o JSON")
    public ResponseEntity<String> handleCallbackPost(
            @RequestParam(value = "mdOrder", required = false) String mdOrder,
            @RequestParam(value = "orderNumber", required = false) String orderNumber,
            @RequestParam(value = "operation", required = false) String operation,
            @RequestParam(value = "status", required = false) Integer status,
            @RequestParam(value = "checksum", required = false) String checksum,
            @RequestParam(value = "sign_alias", required = false) String signAlias,
            @RequestParam(required = false) Map<String, String> allParams,
            @RequestBody(required = false) Map<String, Object> body
    ) {
        LocalDateTime startTime = LocalDateTime.now();
        String operacionLog = "Webhook_Credibanco_Callback_POST";

        if (body != null && !body.isEmpty()) {
            if (mdOrder == null && body.get("mdOrder") != null) mdOrder = String.valueOf(body.get("mdOrder"));
            if (orderNumber == null && body.get("orderNumber") != null) orderNumber = String.valueOf(body.get("orderNumber"));
            if (operation == null && body.get("operation") != null) operation = String.valueOf(body.get("operation"));
            if (status == null && body.get("status") != null) {
                try {
                    status = Integer.parseInt(String.valueOf(body.get("status")));
                } catch (Exception ignored) {}
            }
            if (checksum == null && body.get("checksum") != null) checksum = String.valueOf(body.get("checksum"));
            if (signAlias == null && body.get("sign_alias") != null) signAlias = String.valueOf(body.get("sign_alias"));
        }

        log.info("\n" +
                "================================================================================\n" +
                "[WEBHOOK CREDIBANCO - CALLBACK POST] Notificación de pago entrante\n" +
                "   -> Operación: {}\n" +
                "   -> mdOrder: {}\n" +
                "   -> orderNumber: {}\n" +
                "   -> operation: {}\n" +
                "   -> status: {}\n" +
                "   -> checksum: {}\n" +
                "   -> signAlias: {}\n" +
                "   -> queryParams: {}\n" +
                "   -> body: {}\n" +
                "================================================================================",
                operacionLog, mdOrder, orderNumber, operation, status, checksum, signAlias, allParams, body);

        Map<String, Object> payloadData = new HashMap<>();
        if (allParams != null) {
            payloadData.put("queryParams", allParams);
        }
        if (body != null) {
            payloadData.put("body", body);
        }
        payloadData.putIfAbsent("mdOrder", mdOrder);
        payloadData.putIfAbsent("orderNumber", orderNumber);
        payloadData.putIfAbsent("operation", operation);
        payloadData.putIfAbsent("status", status);
        payloadData.putIfAbsent("checksum", checksum);
        payloadData.putIfAbsent("sign_alias", signAlias);

        String arenaId = resolveArenaId(orderNumber, mdOrder);
        Long usuarioId = resolveUsuarioId(orderNumber, mdOrder);

        try {
            paymentService.processCredibancoCallback(mdOrder, orderNumber, operation, status, checksum, signAlias);

            log.info("[WEBHOOK CREDIBANCO - CALLBACK POST] Procesado con éxito: orderNumber={}, mdOrder={}", orderNumber, mdOrder);

            auditService.logWebhook(
                    IntegrationAuditService.SERVICIO_CREDIBANCO,
                    arenaId,
                    usuarioId,
                    operacionLog,
                    payloadData,
                    "OK",
                    startTime,
                    LocalDateTime.now(),
                    200,
                    "SUCCESS"
            );
            return ResponseEntity.ok("OK");
        } catch (Exception ex) {
            log.error("[WEBHOOK CREDIBANCO - CALLBACK POST] Error al procesar callback: {}", ex.getMessage(), ex);

            auditService.logWebhook(
                    IntegrationAuditService.SERVICIO_CREDIBANCO,
                    arenaId,
                    usuarioId,
                    operacionLog,
                    payloadData,
                    "ERROR: " + ex.getMessage(),
                    startTime,
                    LocalDateTime.now(),
                    500,
                    "FAILED"
            );
            throw ex;
        }
    }

    private String resolveArenaId(String orderNumber, String mdOrder) {
        try {
            if (orderNumber != null && !orderNumber.isBlank()) {
                var p = pedidoPagoRepository.findByReferenciaPago(orderNumber);
                if (p.isPresent() && p.get().getArenaId() != null) return p.get().getArenaId();
            }
            if (mdOrder != null && !mdOrder.isBlank()) {
                var p = pedidoPagoRepository.findByCredibancoOrderId(mdOrder)
                        .or(() -> pedidoPagoRepository.findByMdOrder(mdOrder));
                if (p.isPresent() && p.get().getArenaId() != null) return p.get().getArenaId();
            }
        } catch (Exception ignored) {}
        return "SYSTEM";
    }

    private Long resolveUsuarioId(String orderNumber, String mdOrder) {
        try {
            if (orderNumber != null && !orderNumber.isBlank()) {
                var p = pedidoPagoRepository.findByReferenciaPago(orderNumber);
                if (p.isPresent() && p.get().getUsuarioId() != null) return p.get().getUsuarioId();
            }
            if (mdOrder != null && !mdOrder.isBlank()) {
                var p = pedidoPagoRepository.findByCredibancoOrderId(mdOrder)
                        .or(() -> pedidoPagoRepository.findByMdOrder(mdOrder));
                if (p.isPresent() && p.get().getUsuarioId() != null) return p.get().getUsuarioId();
            }
        } catch (Exception ignored) {}
        return 0L;
    }
}
