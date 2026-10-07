package com.beathub.multiarenas.delivery.payment.controller;

import com.beathub.multiarenas.delivery.payment.service.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/webhook/credibanco")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Credibanco Webhook", description = "Endpoint de notificación callback asíncrono para Credibanco")
public class CredibancoWebhookController {

    private final PaymentService paymentService;

    @GetMapping("/callback")
    @Operation(summary = "Callback GET de notificación de pago", description = "Recibe el resultado enviado por Credibanco via query params")
    public ResponseEntity<String> handleCallbackGet(
            @RequestParam(value = "mdOrder", required = false) String mdOrder,
            @RequestParam(value = "orderNumber", required = false) String orderNumber,
            @RequestParam(value = "operation", required = false) String operation,
            @RequestParam(value = "status", required = false) Integer status,
            @RequestParam(value = "checksum", required = false) String checksum,
            @RequestParam(value = "sign_alias", required = false) String signAlias
    ) {
        log.info("Callback GET recibido de Credibanco: mdOrder={}, orderNumber={}, operation={}, status={}",
                mdOrder, orderNumber, operation, status);

        paymentService.processCredibancoCallback(mdOrder, orderNumber, operation, status, checksum, signAlias);
        return ResponseEntity.ok("OK");
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
            @RequestBody(required = false) java.util.Map<String, Object> body
    ) {
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

        log.info("Callback POST recibido de Credibanco: mdOrder={}, orderNumber={}, operation={}, status={}",
                mdOrder, orderNumber, operation, status);

        paymentService.processCredibancoCallback(mdOrder, orderNumber, operation, status, checksum, signAlias);
        return ResponseEntity.ok("OK");
    }
}
