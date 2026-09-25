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

    @RequestMapping(value = "/callback", method = {RequestMethod.GET, RequestMethod.POST})
    @Operation(summary = "Callback de notificación de pago", description = "Recibe el resultado de la transacción enviado por la pasarela Credibanco")
    public ResponseEntity<String> handleCallback(
            @RequestParam(value = "mdOrder", required = false) String mdOrder,
            @RequestParam(value = "orderNumber", required = false) String orderNumber,
            @RequestParam(value = "operation", required = false) String operation,
            @RequestParam(value = "status", required = false) Integer status,
            @RequestParam(value = "checksum", required = false) String checksum,
            @RequestParam(value = "sign_alias", required = false) String signAlias
    ) {
        log.info("Callback recibido de Credibanco: mdOrder={}, orderNumber={}, operation={}, status={}",
                mdOrder, orderNumber, operation, status);

        paymentService.processCredibancoCallback(mdOrder, orderNumber, operation, status, checksum, signAlias);

        // Credibanco requiere responder HTTP 200 OK
        return ResponseEntity.ok("OK");
    }
}
