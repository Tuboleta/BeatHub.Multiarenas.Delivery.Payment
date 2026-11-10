package com.beathub.multiarenas.delivery.payment.controller;

import com.beathub.multiarenas.delivery.payment.repository.PedidoPagoRepository;
import com.beathub.multiarenas.delivery.payment.service.IntegrationAuditService;
import com.beathub.multiarenas.delivery.payment.service.MercadoPagoService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
@RequestMapping("/webhook/mercadopago")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Mercado Pago Webhook", description = "Endpoint de notificación callback asíncrono para Mercado Pago (ApiOrders)")
public class MercadoPagoWebhookController {

    private final MercadoPagoService mercadoPagoService;
    private final IntegrationAuditService auditService;
    private final PedidoPagoRepository pedidoPagoRepository;
    private final ObjectMapper objectMapper;

    @PostMapping
    @Operation(summary = "Recepción de webhooks de Mercado Pago", description = "Procesa notificaciones automáticas de pedidos y pagos de Mercado Pago")
    public ResponseEntity<String> handleWebhook(
            @RequestHeader(value = "x-signature", required = false) String xSignature,
            @RequestHeader(value = "x-request-id", required = false) String xRequestId,
            @RequestParam(value = "data.id", required = false) String dataIdParam,
            @RequestParam(value = "id", required = false) String idParam,
            @RequestParam(value = "type", required = false) String typeParam,
            @RequestParam(value = "topic", required = false) String topicParam,
            @RequestParam(required = false) Map<String, String> allParams,
            @RequestBody(required = false) String rawBody
    ) {
        LocalDateTime startTime = LocalDateTime.now();

        String finalDataId = dataIdParam != null ? dataIdParam : (idParam != null ? idParam : (allParams != null ? allParams.get("data_id") : null));

        String detectedEvent = resolveEventType(typeParam, topicParam, rawBody);
        String operacionLog = "Webhook_MercadoPago_" + detectedEvent;

        log.info("\n" +
                "================================================================================\n" +
                "[WEBHOOK MERCADOPAGO - NOTIFICACIÓN] Notificación entrante detectada\n" +
                "   -> Operación: {}\n" +
                "   -> x-request-id: {}\n" +
                "   -> x-signature: {}\n" +
                "   -> dataId: {}\n" +
                "   -> type: {}\n" +
                "   -> topic: {}\n" +
                "   -> queryParams: {}\n" +
                "   -> rawBody: {}\n" +
                "================================================================================",
                operacionLog, xRequestId, (xSignature != null && !xSignature.isBlank()) ? "[PRESENTE]" : "[AUSENTE]",
                finalDataId, typeParam, topicParam, allParams, rawBody);

        Map<String, Object> payloadMap = new HashMap<>();
        Map<String, String> headersMap = new HashMap<>();
        if (xRequestId != null) headersMap.put("x-request-id", xRequestId);
        if (xSignature != null) headersMap.put("x-signature", xSignature);
        payloadMap.put("headers", headersMap);
        if (allParams != null && !allParams.isEmpty()) {
            payloadMap.put("queryParams", allParams);
        }
        if (rawBody != null && !rawBody.isBlank()) {
            try {
                JsonNode bodyJson = objectMapper.readTree(rawBody);
                payloadMap.put("body", bodyJson);
            } catch (Exception ignored) {
                payloadMap.put("body", rawBody);
            }
        }
        payloadMap.put("resolvedDataId", finalDataId);
        payloadMap.put("resolvedEvent", detectedEvent);

        String arenaId = resolveArenaId(finalDataId, rawBody);
        Long usuarioId = resolveUsuarioId(finalDataId, rawBody);

        try {
            mercadoPagoService.processWebhook(rawBody, xSignature, xRequestId, finalDataId);

            log.info("[WEBHOOK MERCADOPAGO - NOTIFICACIÓN] Procesado con éxito: operacion={}, dataId={}", operacionLog, finalDataId);

            auditService.logWebhook(
                    IntegrationAuditService.SERVICIO_MERCADOPAGO,
                    arenaId,
                    usuarioId,
                    operacionLog,
                    payloadMap,
                    "OK",
                    startTime,
                    LocalDateTime.now(),
                    200,
                    "SUCCESS"
            );
        } catch (Exception ex) {
            log.error("[WEBHOOK MERCADOPAGO - NOTIFICACIÓN] Error procesando notificación webhook: {}", ex.getMessage(), ex);

            auditService.logWebhook(
                    IntegrationAuditService.SERVICIO_MERCADOPAGO,
                    arenaId,
                    usuarioId,
                    operacionLog,
                    payloadMap,
                    "ERROR: " + ex.getMessage(),
                    startTime,
                    LocalDateTime.now(),
                    500,
                    "FAILED"
            );
        }

        return ResponseEntity.ok("OK");
    }

    @GetMapping
    @Operation(summary = "Ping / Verificación de webhook Mercado Pago", description = "Responde a verificaciones automáticas de disponibilidad de Mercado Pago")
    public ResponseEntity<String> handlePing(@RequestParam(required = false) Map<String, String> allParams) {
        LocalDateTime startTime = LocalDateTime.now();
        String operacionLog = "Webhook_MercadoPago_Ping";

        log.info("\n" +
                "================================================================================\n" +
                "[WEBHOOK MERCADOPAGO - PING / VERIFICACIÓN] Solicitud de verificación recibida\n" +
                "   -> Operación: {}\n" +
                "   -> queryParams: {}\n" +
                "================================================================================",
                operacionLog, allParams);

        auditService.logWebhook(
                IntegrationAuditService.SERVICIO_MERCADOPAGO,
                "SYSTEM",
                0L,
                operacionLog,
                allParams != null ? allParams : Map.of(),
                "OK",
                startTime,
                LocalDateTime.now(),
                200,
                "SUCCESS"
        );

        return ResponseEntity.ok("OK");
    }

    private String resolveEventType(String typeParam, String topicParam, String rawBody) {
        if (typeParam != null && !typeParam.isBlank()) {
            return sanitizeEventName(typeParam);
        }
        if (topicParam != null && !topicParam.isBlank()) {
            return sanitizeEventName(topicParam);
        }
        if (rawBody != null && !rawBody.isBlank()) {
            try {
                JsonNode root = objectMapper.readTree(rawBody);
                if (root.has("action") && !root.get("action").asText().isBlank()) {
                    return sanitizeEventName(root.get("action").asText());
                }
                if (root.has("type") && !root.get("type").asText().isBlank()) {
                    return sanitizeEventName(root.get("type").asText());
                }
                if (root.has("topic") && !root.get("topic").asText().isBlank()) {
                    return sanitizeEventName(root.get("topic").asText());
                }
            } catch (Exception ignored) {}
        }
        return "Notification";
    }

    private String sanitizeEventName(String event) {
        if (event == null) return "Notification";
        String cleaned = event.trim().replaceAll("[^a-zA-Z0-9_.-]", "_");
        if (cleaned.contains("merchant_order")) return "MerchantOrder";
        if (cleaned.contains("payment")) return "Payment";
        if (cleaned.contains("order")) return "Order";
        if (cleaned.contains("chargeback")) return "Chargeback";
        return cleaned.replace('.', '_');
    }

    private String resolveArenaId(String finalDataId, String rawBody) {
        try {
            if (finalDataId != null && !finalDataId.isBlank()) {
                var p = pedidoPagoRepository.findByMpOrderId(finalDataId)
                        .or(() -> pedidoPagoRepository.findByMpPaymentId(finalDataId))
                        .or(() -> pedidoPagoRepository.findByReferenciaPago(finalDataId));
                if (p.isPresent() && p.get().getArenaId() != null) return p.get().getArenaId();
            }
            if (rawBody != null && !rawBody.isBlank()) {
                JsonNode root = objectMapper.readTree(rawBody);
                if (root.has("data") && root.get("data").has("id")) {
                    String dataId = root.get("data").get("id").asText();
                    var p = pedidoPagoRepository.findByMpOrderId(dataId)
                            .or(() -> pedidoPagoRepository.findByMpPaymentId(dataId))
                            .or(() -> pedidoPagoRepository.findByReferenciaPago(dataId));
                    if (p.isPresent() && p.get().getArenaId() != null) return p.get().getArenaId();
                }
            }
        } catch (Exception ignored) {}
        return "SYSTEM";
    }

    private Long resolveUsuarioId(String finalDataId, String rawBody) {
        try {
            if (finalDataId != null && !finalDataId.isBlank()) {
                var p = pedidoPagoRepository.findByMpOrderId(finalDataId)
                        .or(() -> pedidoPagoRepository.findByMpPaymentId(finalDataId))
                        .or(() -> pedidoPagoRepository.findByReferenciaPago(finalDataId));
                if (p.isPresent() && p.get().getUsuarioId() != null) return p.get().getUsuarioId();
            }
            if (rawBody != null && !rawBody.isBlank()) {
                JsonNode root = objectMapper.readTree(rawBody);
                if (root.has("data") && root.get("data").has("id")) {
                    String dataId = root.get("data").get("id").asText();
                    var p = pedidoPagoRepository.findByMpOrderId(dataId)
                            .or(() -> pedidoPagoRepository.findByMpPaymentId(dataId))
                            .or(() -> pedidoPagoRepository.findByReferenciaPago(dataId));
                    if (p.isPresent() && p.get().getUsuarioId() != null) return p.get().getUsuarioId();
                }
            }
        } catch (Exception ignored) {}
        return 0L;
    }
}
