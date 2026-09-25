package com.beathub.multiarenas.delivery.payment.client;

import com.beathub.multiarenas.delivery.payment.client.dto.*;
import com.beathub.multiarenas.delivery.payment.config.CredibancoProperties;
import com.beathub.multiarenas.delivery.payment.service.IntegrationAuditService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
@Slf4j
public class CredibancoRestClient {

    private final RestClient.Builder restClientBuilder;
    private final CredibancoProperties properties;
    private final IntegrationAuditService auditService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 9.2.1 Solicitud de registro del pedido (register.do)
     */
    public CredibancoRegisterResponse registerOrder(
            String orderNumber,
            BigDecimal amount,
            String returnUrl,
            String failUrl,
            String description,
            String jsonParams,
            String clientId,
            String arenaId,
            Long usuarioId
    ) {
        String targetUrl = properties.getBaseUrl() + "/register.do";
        LocalDateTime startTime = LocalDateTime.now();

        // Convertir monto a unidades mínimas (ej: COP 10,000 -> 1000000)
        long minUnitsAmount = amount.multiply(BigDecimal.valueOf(100)).longValue();

        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("userName", properties.getUsername());
        params.add("password", properties.getPassword());
        params.add("orderNumber", orderNumber);
        params.add("amount", String.valueOf(minUnitsAmount));
        params.add("currency", properties.getCurrencyCode());
        params.add("returnUrl", returnUrl != null ? returnUrl : properties.getDefaultReturnUrl());
        if (failUrl != null) {
            params.add("failUrl", failUrl);
        } else if (properties.getDefaultFailUrl() != null) {
            params.add("failUrl", properties.getDefaultFailUrl());
        }
        if (description != null) {
            params.add("description", description);
        }
        if (jsonParams != null && !jsonParams.isEmpty()) {
            params.add("jsonParams", jsonParams);
        }
        if (clientId != null) {
            params.add("clientId", clientId);
        }
        if (properties.getSessionTimeoutSecs() != null) {
            params.add("sessionTimeoutSecs", String.valueOf(properties.getSessionTimeoutSecs()));
        }

        String requestStr = maskSensitive(params.toString());
        CredibancoRegisterResponse response = null;
        Integer httpStatus = 200;
        String responseStr = "";

        try {
            response = restClientBuilder.build()
                    .post()
                    .uri(targetUrl)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(params)
                    .retrieve()
                    .body(CredibancoRegisterResponse.class);

            responseStr = objectMapper.writeValueAsString(response);
            return response;
        } catch (Exception ex) {
            httpStatus = 500;
            responseStr = "Exception: " + ex.getMessage();
            log.error("Error al registrar pedido en Credibanco: {}", ex.getMessage());
            throw new RuntimeException("Credibanco registerOrder failed: " + ex.getMessage(), ex);
        } finally {
            auditService.logExternalCall(
                    arenaId,
                    usuarioId,
                    "CREDIBANCO_REGISTER_ORDER",
                    requestStr,
                    responseStr,
                    startTime,
                    LocalDateTime.now(),
                    httpStatus,
                    (response != null && response.isSuccessful()) ? "SUCCESS" : "FAILED"
            );
        }
    }

    /**
     * 9.2.4 Solicitud ampliada del estado del pedido (getOrderStatusExtended.do)
     */
    public CredibancoStatusResponse getOrderStatusExtended(String orderId, String orderNumber, String arenaId, Long usuarioId) {
        String targetUrl = properties.getBaseUrl() + "/getOrderStatusExtended.do";
        LocalDateTime startTime = LocalDateTime.now();

        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("userName", properties.getUsername());
        params.add("password", properties.getPassword());
        if (orderId != null && !orderId.isEmpty()) {
            params.add("orderId", orderId);
        }
        if (orderNumber != null && !orderNumber.isEmpty()) {
            params.add("orderNumber", orderNumber);
        }

        String requestStr = maskSensitive(params.toString());
        CredibancoStatusResponse response = null;
        Integer httpStatus = 200;
        String responseStr = "";

        try {
            response = restClientBuilder.build()
                    .post()
                    .uri(targetUrl)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(params)
                    .retrieve()
                    .body(CredibancoStatusResponse.class);

            responseStr = objectMapper.writeValueAsString(response);
            return response;
        } catch (Exception ex) {
            httpStatus = 500;
            responseStr = "Exception: " + ex.getMessage();
            log.error("Error al consultar estado en Credibanco: {}", ex.getMessage());
            throw new RuntimeException("Credibanco getOrderStatusExtended failed: " + ex.getMessage(), ex);
        } finally {
            auditService.logExternalCall(
                    arenaId,
                    usuarioId,
                    "CREDIBANCO_GET_ORDER_STATUS",
                    requestStr,
                    responseStr,
                    startTime,
                    LocalDateTime.now(),
                    httpStatus,
                    (response != null && response.isApproved()) ? "SUCCESS" : "FAILED"
            );
        }
    }

    /**
     * 9.2.3 Solicitud de anulación / reembolso (refund.do / reverse.do)
     */
    public CredibancoRefundResponse refundOrder(String orderId, BigDecimal amount, String arenaId, Long usuarioId) {
        String targetUrl = properties.getBaseUrl() + "/refund.do";
        LocalDateTime startTime = LocalDateTime.now();

        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("userName", properties.getUsername());
        params.add("password", properties.getPassword());
        params.add("orderId", orderId);
        if (amount != null) {
            long minUnitsAmount = amount.multiply(BigDecimal.valueOf(100)).longValue();
            params.add("amount", String.valueOf(minUnitsAmount));
        }

        String requestStr = maskSensitive(params.toString());
        CredibancoRefundResponse response = null;
        Integer httpStatus = 200;
        String responseStr = "";

        try {
            response = restClientBuilder.build()
                    .post()
                    .uri(targetUrl)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(params)
                    .retrieve()
                    .body(CredibancoRefundResponse.class);

            responseStr = objectMapper.writeValueAsString(response);
            return response;
        } catch (Exception ex) {
            httpStatus = 500;
            responseStr = "Exception: " + ex.getMessage();
            log.error("Error al anular pedido en Credibanco: {}", ex.getMessage());
            throw new RuntimeException("Credibanco refundOrder failed: " + ex.getMessage(), ex);
        } finally {
            auditService.logExternalCall(
                    arenaId,
                    usuarioId,
                    "CREDIBANCO_REFUND_ORDER",
                    requestStr,
                    responseStr,
                    startTime,
                    LocalDateTime.now(),
                    httpStatus,
                    (response != null && response.isSuccessful()) ? "SUCCESS" : "FAILED"
            );
        }
    }

    /**
     * 9.2.8 Solicitud de consulta de estado de tarjeta (verifyCard.do)
     */
    public CredibancoVerifyCardResponse verifyCard(String pan, String cvc, String expiry, String arenaId, Long usuarioId) {
        String targetUrl = properties.getBaseUrl() + "/verifyCard.do";
        LocalDateTime startTime = LocalDateTime.now();

        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("userName", properties.getUsername());
        params.add("password", properties.getPassword());
        params.add("pan", pan);
        params.add("cvc", cvc);
        params.add("expiry", expiry); // YYYYMM

        String requestStr = "pan=***" + (pan != null && pan.length() > 4 ? pan.substring(pan.length() - 4) : "") + ", expiry=" + expiry;
        CredibancoVerifyCardResponse response = null;
        Integer httpStatus = 200;
        String responseStr = "";

        try {
            response = restClientBuilder.build()
                    .post()
                    .uri(targetUrl)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(params)
                    .retrieve()
                    .body(CredibancoVerifyCardResponse.class);

            responseStr = objectMapper.writeValueAsString(response);
            return response;
        } catch (Exception ex) {
            httpStatus = 500;
            responseStr = "Exception: " + ex.getMessage();
            log.error("Error al verificar tarjeta en Credibanco: {}", ex.getMessage());
            throw new RuntimeException("Credibanco verifyCard failed: " + ex.getMessage(), ex);
        } finally {
            auditService.logExternalCall(
                    arenaId,
                    usuarioId,
                    "CREDIBANCO_VERIFY_CARD",
                    requestStr,
                    responseStr,
                    startTime,
                    LocalDateTime.now(),
                    httpStatus,
                    (response != null && response.isSuccessful()) ? "SUCCESS" : "FAILED"
            );
        }
    }

    private String maskSensitive(String input) {
        if (input == null) return "";
        return input.replaceAll("password=[^&,\\]]+", "password=******");
    }
}
