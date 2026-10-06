package com.beathub.multiarenas.delivery.payment.client;

import com.beathub.multiarenas.delivery.payment.client.dto.*;
import com.beathub.multiarenas.delivery.payment.config.CredibancoProperties;
import com.beathub.multiarenas.delivery.payment.exception.CredibancoApiException;
import com.beathub.multiarenas.delivery.payment.service.IntegrationAuditService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpStatusCodeException;
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
    private final ObjectMapper objectMapper;

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

        String requestStr = formatRequestLog("POST", targetUrl, toMaskedMap(params));
        CredibancoRegisterResponse response = null;
        Integer httpStatus = 200;
        String responseStr = "";

        try {
            responseStr = restClientBuilder.build()
                    .post()
                    .uri(targetUrl)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .accept(MediaType.APPLICATION_JSON, MediaType.TEXT_PLAIN)
                    .body(params)
                    .retrieve()
                    .body(String.class);

            log.info("Credibanco registerOrder raw response: {}", responseStr);
            if (responseStr != null && !responseStr.isBlank()) {
                response = objectMapper.readValue(responseStr, CredibancoRegisterResponse.class);
            }
            return response;
        } catch (HttpStatusCodeException ex) {
            httpStatus = ex.getStatusCode().value();
            responseStr = ex.getResponseBodyAsString();
            log.error("Error HTTP ({}) al registrar pedido en Credibanco: {}", httpStatus, responseStr);
            throw new CredibancoApiException(String.valueOf(httpStatus), "Error de comunicación con Credibanco: " + responseStr, ex);
        } catch (Exception ex) {
            httpStatus = 500;
            if (responseStr == null || responseStr.isBlank()) {
                responseStr = "Exception: " + ex.getMessage();
            }
            log.error("Error al registrar pedido en Credibanco. Respuesta: '{}'. Error: {}", responseStr, ex.getMessage());
            throw new CredibancoApiException("GATEWAY_ERROR", "Error al procesar registro en pasarela Credibanco: " + ex.getMessage(), ex);
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

        String requestStr = formatRequestLog("POST", targetUrl, toMaskedMap(params));
        CredibancoStatusResponse response = null;
        Integer httpStatus = 200;
        String responseStr = "";

        try {
            responseStr = restClientBuilder.build()
                    .post()
                    .uri(targetUrl)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .accept(MediaType.APPLICATION_JSON, MediaType.TEXT_PLAIN)
                    .body(params)
                    .retrieve()
                    .body(String.class);

            log.info("Credibanco getOrderStatusExtended raw response: {}", responseStr);
            if (responseStr != null && !responseStr.isBlank()) {
                response = objectMapper.readValue(responseStr, CredibancoStatusResponse.class);
            }
            return response;
        } catch (HttpStatusCodeException ex) {
            httpStatus = ex.getStatusCode().value();
            responseStr = ex.getResponseBodyAsString();
            log.error("Error HTTP ({}) al consultar estado en Credibanco: {}", httpStatus, responseStr);
            throw new CredibancoApiException(String.valueOf(httpStatus), "Error de comunicación con Credibanco: " + responseStr, ex);
        } catch (Exception ex) {
            httpStatus = 500;
            if (responseStr == null || responseStr.isBlank()) {
                responseStr = "Exception: " + ex.getMessage();
            }
            log.error("Error al consultar estado en Credibanco. Respuesta: '{}'. Error: {}", responseStr, ex.getMessage());
            throw new CredibancoApiException("GATEWAY_ERROR", "Error al consultar estado en pasarela Credibanco: " + ex.getMessage(), ex);
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

        String requestStr = formatRequestLog("POST", targetUrl, toMaskedMap(params));
        CredibancoRefundResponse response = null;
        Integer httpStatus = 200;
        String responseStr = "";

        try {
            responseStr = restClientBuilder.build()
                    .post()
                    .uri(targetUrl)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .accept(MediaType.APPLICATION_JSON, MediaType.TEXT_PLAIN)
                    .body(params)
                    .retrieve()
                    .body(String.class);

            log.info("Credibanco refundOrder raw response: {}", responseStr);
            if (responseStr != null && !responseStr.isBlank()) {
                response = objectMapper.readValue(responseStr, CredibancoRefundResponse.class);
            }
            return response;
        } catch (HttpStatusCodeException ex) {
            httpStatus = ex.getStatusCode().value();
            responseStr = ex.getResponseBodyAsString();
            log.error("Error HTTP ({}) al anular pedido en Credibanco: {}", httpStatus, responseStr);
            throw new CredibancoApiException(String.valueOf(httpStatus), "Error de comunicación con Credibanco: " + responseStr, ex);
        } catch (Exception ex) {
            httpStatus = 500;
            if (responseStr == null || responseStr.isBlank()) {
                responseStr = "Exception: " + ex.getMessage();
            }
            log.error("Error al anular pedido en Credibanco. Respuesta: '{}'. Error: {}", responseStr, ex.getMessage());
            throw new CredibancoApiException("GATEWAY_ERROR", "Error al anular pedido en pasarela Credibanco: " + ex.getMessage(), ex);
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

        java.util.Map<String, Object> cardParams = new java.util.LinkedHashMap<>();
        cardParams.put("pan", "pan=***" + (pan != null && pan.length() > 4 ? pan.substring(pan.length() - 4) : ""));
        cardParams.put("cvc", "******");
        cardParams.put("expiry", expiry);
        String requestStr = formatRequestLog("POST", targetUrl, cardParams);

        CredibancoVerifyCardResponse response = null;
        Integer httpStatus = 200;
        String responseStr = "";

        try {
            responseStr = restClientBuilder.build()
                    .post()
                    .uri(targetUrl)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .accept(MediaType.APPLICATION_JSON, MediaType.TEXT_PLAIN)
                    .body(params)
                    .retrieve()
                    .body(String.class);

            log.info("Credibanco verifyCard raw response: {}", responseStr);
            if (responseStr != null && !responseStr.isBlank()) {
                response = objectMapper.readValue(responseStr, CredibancoVerifyCardResponse.class);
            }
            return response;
        } catch (HttpStatusCodeException ex) {
            httpStatus = ex.getStatusCode().value();
            responseStr = ex.getResponseBodyAsString();
            log.error("Error HTTP ({}) al verificar tarjeta en Credibanco: {}", httpStatus, responseStr);
            throw new CredibancoApiException(String.valueOf(httpStatus), "Error de comunicación con Credibanco: " + responseStr, ex);
        } catch (Exception ex) {
            httpStatus = 500;
            if (responseStr == null || responseStr.isBlank()) {
                responseStr = "Exception: " + ex.getMessage();
            }
            log.error("Error al verificar tarjeta en Credibanco. Respuesta: '{}'. Error: {}", responseStr, ex.getMessage());
            throw new CredibancoApiException("GATEWAY_ERROR", "Error al verificar tarjeta en pasarela Credibanco: " + ex.getMessage(), ex);
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

    /**
     * 9.2.9 Solicitud de pago con tarjeta tokenizada (paymentOrderBinding.do)
     */
    public CredibancoPaymentOrderResponse paymentOrderBinding(
            String mdOrder,
            String bindingId,
            String cvc,
            String ip,
            String arenaId,
            Long usuarioId
    ) {
        String targetUrl = properties.getBaseUrl() + "/paymentOrderBinding.do";
        LocalDateTime startTime = LocalDateTime.now();

        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("userName", properties.getUsername());
        params.add("password", properties.getPassword());
        params.add("mdOrder", mdOrder);
        params.add("bindingId", bindingId);
        if (cvc != null && !cvc.isBlank()) {
            params.add("cvc", cvc);
        }
        if (ip != null && !ip.isBlank()) {
            params.add("ip", ip);
        }

        java.util.Map<String, Object> maskedMap = new java.util.LinkedHashMap<>();
        maskedMap.put("mdOrder", mdOrder);
        maskedMap.put("bindingId", bindingId);
        if (cvc != null && !cvc.isBlank()) {
            maskedMap.put("cvc", "******");
        }
        if (ip != null) {
            maskedMap.put("ip", ip);
        }
        String requestStr = formatRequestLog("POST", targetUrl, maskedMap);

        CredibancoPaymentOrderResponse response = null;
        Integer httpStatus = 200;
        String responseStr = "";

        try {
            responseStr = restClientBuilder.build()
                    .post()
                    .uri(targetUrl)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .accept(MediaType.APPLICATION_JSON, MediaType.TEXT_PLAIN)
                    .body(params)
                    .retrieve()
                    .body(String.class);

            log.info("Credibanco paymentOrderBinding raw response: {}", responseStr);
            if (responseStr != null && !responseStr.isBlank()) {
                response = objectMapper.readValue(responseStr, CredibancoPaymentOrderResponse.class);
            }
            return response;
        } catch (HttpStatusCodeException ex) {
            httpStatus = ex.getStatusCode().value();
            responseStr = ex.getResponseBodyAsString();
            log.error("Error HTTP ({}) al ejecutar pago con token en Credibanco: {}", httpStatus, responseStr);
            throw new CredibancoApiException(String.valueOf(httpStatus), "Error de comunicación con Credibanco: " + responseStr, ex);
        } catch (Exception ex) {
            httpStatus = 500;
            if (responseStr == null || responseStr.isBlank()) {
                responseStr = "Exception: " + ex.getMessage();
            }
            log.error("Error al ejecutar pago con token en Credibanco: {}", ex.getMessage());
            throw new CredibancoApiException("GATEWAY_ERROR", "Error al procesar pago tokenizado en Credibanco: " + ex.getMessage(), ex);
        } finally {
            auditService.logExternalCall(
                    arenaId,
                    usuarioId,
                    "CREDIBANCO_PAYMENT_ORDER_BINDING",
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
     * 9.2.10 Consulta de tarjetas vinculadas del cliente (getBindings.do)
     */
    public CredibancoGetBindingsResponse getBindings(String clientId, String arenaId, Long usuarioId) {
        String targetUrl = properties.getBaseUrl() + "/getBindings.do";
        LocalDateTime startTime = LocalDateTime.now();

        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("userName", properties.getUsername());
        params.add("password", properties.getPassword());
        params.add("clientId", clientId);

        String requestStr = formatRequestLog("POST", targetUrl, toMaskedMap(params));
        CredibancoGetBindingsResponse response = null;
        Integer httpStatus = 200;
        String responseStr = "";

        try {
            responseStr = restClientBuilder.build()
                    .post()
                    .uri(targetUrl)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .accept(MediaType.APPLICATION_JSON, MediaType.TEXT_PLAIN)
                    .body(params)
                    .retrieve()
                    .body(String.class);

            log.info("Credibanco getBindings raw response: {}", responseStr);
            if (responseStr != null && !responseStr.isBlank()) {
                response = objectMapper.readValue(responseStr, CredibancoGetBindingsResponse.class);
            }
            return response;
        } catch (HttpStatusCodeException ex) {
            httpStatus = ex.getStatusCode().value();
            responseStr = ex.getResponseBodyAsString();
            log.error("Error HTTP ({}) al consultar bindings en Credibanco: {}", httpStatus, responseStr);
            throw new CredibancoApiException(String.valueOf(httpStatus), "Error de comunicación con Credibanco: " + responseStr, ex);
        } catch (Exception ex) {
            httpStatus = 500;
            if (responseStr == null || responseStr.isBlank()) {
                responseStr = "Exception: " + ex.getMessage();
            }
            log.error("Error al consultar bindings en Credibanco: {}", ex.getMessage());
            throw new CredibancoApiException("GATEWAY_ERROR", "Error al consultar tarjetas en Credibanco: " + ex.getMessage(), ex);
        } finally {
            auditService.logExternalCall(
                    arenaId,
                    usuarioId,
                    "CREDIBANCO_GET_BINDINGS",
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
     * 9.2.11 Desvinculación de tarjeta (unBindCard.do)
     */
    public boolean unBindCard(String bindingId, String arenaId, Long usuarioId) {
        String targetUrl = properties.getBaseUrl() + "/unBindCard.do";
        LocalDateTime startTime = LocalDateTime.now();

        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("userName", properties.getUsername());
        params.add("password", properties.getPassword());
        params.add("bindingId", bindingId);

        String requestStr = formatRequestLog("POST", targetUrl, toMaskedMap(params));
        Integer httpStatus = 200;
        String responseStr = "";
        boolean success = false;

        try {
            responseStr = restClientBuilder.build()
                    .post()
                    .uri(targetUrl)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .accept(MediaType.APPLICATION_JSON, MediaType.TEXT_PLAIN)
                    .body(params)
                    .retrieve()
                    .body(String.class);

            log.info("Credibanco unBindCard raw response: {}", responseStr);
            if (responseStr != null && !responseStr.isBlank()) {
                com.fasterxml.jackson.databind.JsonNode node = objectMapper.readTree(responseStr);
                String errorCode = node.has("errorCode") ? node.get("errorCode").asText() : null;
                success = "0".equals(errorCode);
            }
            return success;
        } catch (HttpStatusCodeException ex) {
            httpStatus = ex.getStatusCode().value();
            responseStr = ex.getResponseBodyAsString();
            log.error("Error HTTP ({}) al desvincular tarjeta en Credibanco: {}", httpStatus, responseStr);
            throw new CredibancoApiException(String.valueOf(httpStatus), "Error de comunicación con Credibanco: " + responseStr, ex);
        } catch (Exception ex) {
            httpStatus = 500;
            if (responseStr == null || responseStr.isBlank()) {
                responseStr = "Exception: " + ex.getMessage();
            }
            log.error("Error al desvincular tarjeta en Credibanco: {}", ex.getMessage());
            throw new CredibancoApiException("GATEWAY_ERROR", "Error al desvincular tarjeta en Credibanco: " + ex.getMessage(), ex);
        } finally {
            auditService.logExternalCall(
                    arenaId,
                    usuarioId,
                    "CREDIBANCO_UNBIND_CARD",
                    requestStr,
                    responseStr,
                    startTime,
                    LocalDateTime.now(),
                    httpStatus,
                    success ? "SUCCESS" : "FAILED"
            );
        }
    }

    private String formatRequestLog(String method, String endpoint, Object body) {
        try {
            java.util.Map<String, Object> logMap = new java.util.LinkedHashMap<>();
            logMap.put("method", method);
            logMap.put("endpoint", endpoint != null ? endpoint : "");
            logMap.put("body", body);
            return objectMapper.writeValueAsString(logMap);
        } catch (Exception e) {
            return method + " " + endpoint;
        }
    }

    private java.util.Map<String, Object> toMaskedMap(MultiValueMap<String, String> params) {
        java.util.Map<String, Object> map = new java.util.LinkedHashMap<>();
        if (params != null) {
            for (java.util.Map.Entry<String, java.util.List<String>> entry : params.entrySet()) {
                if ("password".equalsIgnoreCase(entry.getKey()) || "cvc".equalsIgnoreCase(entry.getKey())) {
                    map.put(entry.getKey(), "******");
                } else if ("pan".equalsIgnoreCase(entry.getKey())) {
                    String panVal = entry.getValue() != null && !entry.getValue().isEmpty() ? entry.getValue().get(0) : "";
                    map.put(entry.getKey(), "pan=***" + (panVal.length() > 4 ? panVal.substring(panVal.length() - 4) : ""));
                } else {
                    map.put(entry.getKey(), entry.getValue() != null && entry.getValue().size() == 1 ? entry.getValue().get(0) : entry.getValue());
                }
            }
        }
        return map;
    }

    private String maskSensitive(String input) {
        if (input == null) return "";
        return input.replaceAll("password=[^&,\\]]+", "password=******");
    }
}
