package com.beathub.multiarenas.delivery.payment.controller;

import com.beathub.multiarenas.delivery.payment.service.MercadoPagoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/webhook/mercadopago")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Mercado Pago Webhook", description = "Endpoint de notificación callback asíncrono para Mercado Pago (ApiOrders)")
public class MercadoPagoWebhookController {

    private final MercadoPagoService mercadoPagoService;

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
        String finalDataId = dataIdParam != null ? dataIdParam : (idParam != null ? idParam : (allParams != null ? allParams.get("data_id") : null));
        log.info("🔔 [MercadoPago Webhook] POST Recibido! xRequestId={}, dataId={}, type={}, topic={}, queryParams={}, body={}",
                xRequestId, finalDataId, typeParam, topicParam, allParams, rawBody);

        try {
            mercadoPagoService.processWebhook(rawBody, xSignature, xRequestId, finalDataId);
        } catch (Exception ex) {
            log.error("❌ [MercadoPago Webhook] Error procesando notificación webhook: {}", ex.getMessage(), ex);
        }

        return ResponseEntity.ok("OK");
    }

    @GetMapping
    @Operation(summary = "Ping / Verificación de webhook Mercado Pago", description = "Responde a verificaciones automáticas de disponibilidad de Mercado Pago")
    public ResponseEntity<String> handlePing(@RequestParam(required = false) Map<String, String> allParams) {
        log.info("🔔 [MercadoPago Webhook] GET/Ping de verificación recibido! params={}", allParams);
        return ResponseEntity.ok("OK");
    }
}

