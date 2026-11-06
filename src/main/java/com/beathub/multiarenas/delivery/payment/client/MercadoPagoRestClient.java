package com.beathub.multiarenas.delivery.payment.client;

import com.beathub.multiarenas.delivery.payment.client.dto.mercadopago.*;
import com.beathub.multiarenas.delivery.payment.config.MercadoPagoProperties;
import com.beathub.multiarenas.delivery.payment.exception.MercadoPagoApiException;
import com.beathub.multiarenas.delivery.payment.service.IntegrationAuditService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class MercadoPagoRestClient {

    private final RestClient.Builder restClientBuilder;
    private final MercadoPagoProperties properties;
    private final IntegrationAuditService auditService;
    private final ObjectMapper objectMapper;

    private static final int SERVICIO_MERCADOPAGO_ID = 1;

    private String getSanitizedToken() {
        String token = properties.getAccessToken();
        if (token == null || token.isBlank()) {
            throw new MercadoPagoApiException("TOKEN_MISSING", "Mercado Pago Access Token no configurado");
        }
        return token.replace("Bearer ", "").trim();
    }

    /**
     * Crear Checkout Preference (Checkout Pro) en Mercado Pago (POST /checkout/preferences)
     * Retorna la URL de checkout (init_point / sandbox_init_point).
     */
    public MpPreferenceResponse createPreference(MpPreferenceRequest request, String arenaId, Long usuarioId) {
        String targetUrl = properties.getBaseUrl() + "/checkout/preferences";
        LocalDateTime startTime = LocalDateTime.now();

        String requestJson = "";
        try {
            requestJson = objectMapper.writeValueAsString(request);
        } catch (Exception ignored) {}

        log.info("[MercadoPagoRestClient] Creando Checkout Preference para ref={}: {}", request.getExternalReference(), requestJson);

        try {
            RestClient client = restClientBuilder.build();
            String responseBody = client.post()
                    .uri(targetUrl)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + getSanitizedToken())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestJson)
                    .retrieve()
                    .body(String.class);

            auditService.logExternalCall(IntegrationAuditService.SERVICIO_MERCADOPAGO,
                    arenaId,
                    usuarioId,
                    "MercadoPago_CreatePreference",
                    requestJson,
                    responseBody,
                    startTime,
                    LocalDateTime.now(),
                    200,
                    "SUCCESS"
            );

            log.info("[MercadoPagoRestClient] Preference creada exitosamente: {}", responseBody);
            return objectMapper.readValue(responseBody, MpPreferenceResponse.class);

        } catch (HttpStatusCodeException ex) {
            String errBody = ex.getResponseBodyAsString();
            auditService.logExternalCall(IntegrationAuditService.SERVICIO_MERCADOPAGO,
                    arenaId,
                    usuarioId,
                    "MercadoPago_CreatePreference",
                    requestJson,
                    errBody,
                    startTime,
                    LocalDateTime.now(),
                    ex.getStatusCode().value(),
                    "FAILED"
            );
            log.error("[MercadoPagoRestClient] Error HTTP al crear preference: {} - {}", ex.getStatusCode(), errBody);
            throw new MercadoPagoApiException("MP_PREFERENCE_ERROR", "Error creando preference en Mercado Pago (" + ex.getStatusCode() + "): " + errBody, ex);
        } catch (Exception ex) {
            auditService.logExternalCall(IntegrationAuditService.SERVICIO_MERCADOPAGO,
                    arenaId,
                    usuarioId,
                    "MercadoPago_CreatePreference",
                    requestJson,
                    ex.getMessage(),
                    startTime,
                    LocalDateTime.now(),
                    500,
                    "EXCEPTION"
            );
            log.error("[MercadoPagoRestClient] Excepción al crear preference: {}", ex.getMessage(), ex);
            throw new MercadoPagoApiException("MP_PREFERENCE_EXCEPTION", "Error interno al invocar creación de preference en Mercado Pago: " + ex.getMessage(), ex);
        }
    }

    /**
     * Consultar detalle de Pago en Mercado Pago (GET /v1/payments/{paymentId})
     */
    public MpPaymentDetailResponse getPayment(String paymentId, String arenaId, Long usuarioId) {
        String targetUrl = properties.getBaseUrl() + "/v1/payments/" + paymentId;
        LocalDateTime startTime = LocalDateTime.now();

        try {
            RestClient client = restClientBuilder.build();
            String responseBody = client.get()
                    .uri(targetUrl)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + getSanitizedToken())
                    .retrieve()
                    .body(String.class);

            auditService.logExternalCall(IntegrationAuditService.SERVICIO_MERCADOPAGO,
                    arenaId,
                    usuarioId,
                    "MercadoPago_GetPayment",
                    "paymentId=" + paymentId,
                    responseBody,
                    startTime,
                    LocalDateTime.now(),
                    200,
                    "SUCCESS"
            );

            return objectMapper.readValue(responseBody, MpPaymentDetailResponse.class);
        } catch (HttpStatusCodeException ex) {
            String errBody = ex.getResponseBodyAsString();
            auditService.logExternalCall(IntegrationAuditService.SERVICIO_MERCADOPAGO,
                    arenaId,
                    usuarioId,
                    "MercadoPago_GetPayment",
                    "paymentId=" + paymentId,
                    errBody,
                    startTime,
                    LocalDateTime.now(),
                    ex.getStatusCode().value(),
                    "FAILED"
            );
            log.warn("[MercadoPagoRestClient] Error consultando pago {}: {} - {}", paymentId, ex.getStatusCode(), errBody);
            throw new MercadoPagoApiException("MP_GET_PAYMENT_ERROR", "Error consultando pago en Mercado Pago: " + errBody, ex);
        } catch (Exception ex) {
            log.error("[MercadoPagoRestClient] Excepción consultando pago {}: {}", paymentId, ex.getMessage(), ex);
            throw new MercadoPagoApiException("MP_GET_PAYMENT_EXCEPTION", "Error consultando pago: " + ex.getMessage(), ex);
        }
    }

    /**
     * Buscar Pagos por Referencia Externa (GET /v1/payments/search?external_reference={externalReference})
     */
    public List<MpPaymentDetailResponse> searchPaymentByExternalReference(String externalReference, String arenaId, Long usuarioId) {
        String targetUrl = properties.getBaseUrl() + "/v1/payments/search?external_reference=" + externalReference;
        LocalDateTime startTime = LocalDateTime.now();

        try {
            RestClient client = restClientBuilder.build();
            String responseBody = client.get()
                    .uri(targetUrl)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + getSanitizedToken())
                    .retrieve()
                    .body(String.class);

            auditService.logExternalCall(IntegrationAuditService.SERVICIO_MERCADOPAGO,
                    arenaId,
                    usuarioId,
                    "MercadoPago_SearchPayment",
                    "ref=" + externalReference,
                    responseBody,
                    startTime,
                    LocalDateTime.now(),
                    200,
                    "SUCCESS"
            );

            MpPaymentDetailResponse.SearchResult result = objectMapper.readValue(responseBody, MpPaymentDetailResponse.SearchResult.class);
            return result != null && result.getResults() != null ? result.getResults() : Collections.emptyList();
        } catch (HttpStatusCodeException ex) {
            String errBody = ex.getResponseBodyAsString();
            auditService.logExternalCall(IntegrationAuditService.SERVICIO_MERCADOPAGO,
                    arenaId,
                    usuarioId,
                    "MercadoPago_SearchPayment",
                    "ref=" + externalReference,
                    errBody,
                    startTime,
                    LocalDateTime.now(),
                    ex.getStatusCode().value(),
                    "FAILED"
            );
            log.warn("[MercadoPagoRestClient] Error buscando pagos por ref {}: {} - {}", externalReference, ex.getStatusCode(), errBody);
            return Collections.emptyList();
        } catch (Exception ex) {
            log.warn("[MercadoPagoRestClient] Excepción buscando pagos por ref {}: {}", externalReference, ex.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * 1. Crear Orden con la moderna API de Orders de Mercado Pago (POST /v1/orders)
     */
    public MpOrderResponse createOrder(MpOrderRequest request, String arenaId, Long usuarioId, String idempotencyKey) {
        String targetUrl = properties.getBaseUrl() + "/v1/orders";
        LocalDateTime startTime = LocalDateTime.now();
        String idempotency = (idempotencyKey != null && !idempotencyKey.isBlank())
                ? idempotencyKey
                : UUID.randomUUID().toString();

        String requestJson = "";
        try {
            requestJson = objectMapper.writeValueAsString(request);
        } catch (Exception ignored) {}

        log.info("[MercadoPagoRestClient] Creando orden (v1/orders) para ref={}: {}", request.getExternalReference(), requestJson);

        try {
            RestClient client = restClientBuilder.build();
            String responseBody = client.post()
                    .uri(targetUrl)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + getSanitizedToken())
                    .header("X-Idempotency-Key", idempotency)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestJson)
                    .retrieve()
                    .body(String.class);

            auditService.logExternalCall(IntegrationAuditService.SERVICIO_MERCADOPAGO, 
                    arenaId,
                    usuarioId,
                    "MercadoPago_CreateOrder",
                    requestJson,
                    responseBody,
                    startTime,
                    LocalDateTime.now(),
                    200,
                    "SUCCESS"
            );

            log.info("[MercadoPagoRestClient] Orden creada exitosamente: {}", responseBody);
            return objectMapper.readValue(responseBody, MpOrderResponse.class);

        } catch (HttpStatusCodeException ex) {
            String errBody = ex.getResponseBodyAsString();
            auditService.logExternalCall(IntegrationAuditService.SERVICIO_MERCADOPAGO, 
                    arenaId,
                    usuarioId,
                    "MercadoPago_CreateOrder",
                    requestJson,
                    errBody,
                    startTime,
                    LocalDateTime.now(),
                    ex.getStatusCode().value(),
                    "FAILED"
            );
            log.error("[MercadoPagoRestClient] Error HTTP al crear orden: {} - {}", ex.getStatusCode(), errBody);
            throw new MercadoPagoApiException("MP_ORDER_ERROR", "Error creando orden en Mercado Pago (" + ex.getStatusCode() + "): " + errBody, ex);
        } catch (Exception ex) {
            auditService.logExternalCall(IntegrationAuditService.SERVICIO_MERCADOPAGO, 
                    arenaId,
                    usuarioId,
                    "MercadoPago_CreateOrder",
                    requestJson,
                    ex.getMessage(),
                    startTime,
                    LocalDateTime.now(),
                    500,
                    "EXCEPTION"
            );
            log.error("[MercadoPagoRestClient] Excepción al crear orden: {}", ex.getMessage(), ex);
            throw new MercadoPagoApiException("MP_ORDER_EXCEPTION", "Error interno comunicando con Mercado Pago: " + ex.getMessage(), ex);
        }
    }

    /**
     * 2. Consultar Orden por ID (GET /v1/orders/{orderId})
     */
    public MpOrderResponse getOrder(String orderId, String arenaId, Long usuarioId) {
        String targetUrl = properties.getBaseUrl() + "/v1/orders/" + orderId;
        LocalDateTime startTime = LocalDateTime.now();

        try {
            RestClient client = restClientBuilder.build();
            String responseBody = client.get()
                    .uri(targetUrl)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + getSanitizedToken())
                    .retrieve()
                    .body(String.class);

            auditService.logExternalCall(IntegrationAuditService.SERVICIO_MERCADOPAGO, 
                    arenaId,
                    usuarioId,
                    "MercadoPago_GetOrder",
                    "orderId=" + orderId,
                    responseBody,
                    startTime,
                    LocalDateTime.now(),
                    200,
                    "SUCCESS"
            );

            return objectMapper.readValue(responseBody, MpOrderResponse.class);

        } catch (HttpStatusCodeException ex) {
            String errBody = ex.getResponseBodyAsString();
            auditService.logExternalCall(IntegrationAuditService.SERVICIO_MERCADOPAGO, 
                    arenaId,
                    usuarioId,
                    "MercadoPago_GetOrder",
                    "orderId=" + orderId,
                    errBody,
                    startTime,
                    LocalDateTime.now(),
                    ex.getStatusCode().value(),
                    "FAILED"
            );
            log.warn("[MercadoPagoRestClient] Error consultando orden {}: {} - {}", orderId, ex.getStatusCode(), errBody);
            throw new MercadoPagoApiException("MP_GET_ORDER_ERROR", "Error al consultar orden en Mercado Pago: " + errBody, ex);
        } catch (Exception ex) {
            log.error("[MercadoPagoRestClient] Excepción consultando orden {}: {}", orderId, ex.getMessage(), ex);
            throw new MercadoPagoApiException("MP_GET_ORDER_EXCEPTION", "Error consultando orden: " + ex.getMessage(), ex);
        }
    }

    /**
     * 3. Reembolso total o parcial de Orden (POST /v1/orders/{orderId}/refund)
     */
    public String refundOrder(String orderId, String paymentId, BigDecimal amount, String arenaId, Long usuarioId) {
        String targetUrl = properties.getBaseUrl() + "/v1/orders/" + orderId + "/refund";
        LocalDateTime startTime = LocalDateTime.now();

        MpRefundOrderRequest refundReq;
        if (paymentId != null && !paymentId.isBlank()) {
            MpRefundOrderRequest.TransactionRefund item = MpRefundOrderRequest.TransactionRefund.builder()
                    .id(paymentId)
                    .amount(amount != null ? amount.toPlainString() : null)
                    .build();
            refundReq = MpRefundOrderRequest.builder()
                    .transactions(List.of(item))
                    .build();
        } else {
            refundReq = MpRefundOrderRequest.builder().build();
        }

        String reqJson = "";
        try {
            reqJson = objectMapper.writeValueAsString(refundReq);
        } catch (Exception ignored) {}

        try {
            RestClient client = restClientBuilder.build();
            String responseBody = client.post()
                    .uri(targetUrl)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + getSanitizedToken())
                    .header("X-Idempotency-Key", UUID.randomUUID().toString())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(reqJson)
                    .retrieve()
                    .body(String.class);

            auditService.logExternalCall(IntegrationAuditService.SERVICIO_MERCADOPAGO, 
                    arenaId,
                    usuarioId,
                    "MercadoPago_RefundOrder",
                    reqJson,
                    responseBody,
                    startTime,
                    LocalDateTime.now(),
                    200,
                    "SUCCESS"
            );

            return responseBody;
        } catch (HttpStatusCodeException ex) {
            String errBody = ex.getResponseBodyAsString();
            auditService.logExternalCall(IntegrationAuditService.SERVICIO_MERCADOPAGO, 
                    arenaId,
                    usuarioId,
                    "MercadoPago_RefundOrder",
                    reqJson,
                    errBody,
                    startTime,
                    LocalDateTime.now(),
                    ex.getStatusCode().value(),
                    "FAILED"
            );
            log.error("[MercadoPagoRestClient] Error realizando reembolso de orden {}: {}", orderId, errBody);
            throw new MercadoPagoApiException("MP_REFUND_ERROR", "Error al solicitar reembolso en Mercado Pago: " + errBody, ex);
        } catch (Exception ex) {
            throw new MercadoPagoApiException("MP_REFUND_EXCEPTION", "Excepción al solicitar reembolso: " + ex.getMessage(), ex);
        }
    }

    /**
     * 4. Crear Cliente en Mercado Pago (POST /v1/customers)
     */
    public MpCustomerResponse createCustomer(MpCustomerRequest request, String arenaId, Long usuarioId) {
        String targetUrl = properties.getBaseUrl() + "/v1/customers";
        LocalDateTime startTime = LocalDateTime.now();
        String reqJson = "";
        try {
            reqJson = objectMapper.writeValueAsString(request);
        } catch (Exception ignored) {}

        try {
            RestClient client = restClientBuilder.build();
            String responseBody = client.post()
                    .uri(targetUrl)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + getSanitizedToken())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(reqJson)
                    .retrieve()
                    .body(String.class);

            auditService.logExternalCall(IntegrationAuditService.SERVICIO_MERCADOPAGO, 
                    arenaId,
                    usuarioId,
                    "MercadoPago_CreateCustomer",
                    reqJson,
                    responseBody,
                    startTime,
                    LocalDateTime.now(),
                    200,
                    "SUCCESS"
            );

            return objectMapper.readValue(responseBody, MpCustomerResponse.class);
        } catch (HttpStatusCodeException ex) {
            String errBody = ex.getResponseBodyAsString();
            auditService.logExternalCall(IntegrationAuditService.SERVICIO_MERCADOPAGO, arenaId, usuarioId, "MercadoPago_CreateCustomer", reqJson, errBody, startTime, LocalDateTime.now(), ex.getStatusCode().value(), "FAILED");
            throw new MercadoPagoApiException("MP_CUSTOMER_ERROR", "Error creando cliente en Mercado Pago: " + errBody, ex);
        } catch (Exception ex) {
            throw new MercadoPagoApiException("MP_CUSTOMER_EXCEPTION", "Excepción creando cliente: " + ex.getMessage(), ex);
        }
    }

    /**
     * 5. Buscar Cliente por Email en Mercado Pago (GET /v1/customers/search?email=...)
     */
    public MpCustomerResponse searchCustomer(String email, String arenaId, Long usuarioId) {
        String targetUrl = properties.getBaseUrl() + "/v1/customers/search?email=" + email;
        LocalDateTime startTime = LocalDateTime.now();

        try {
            RestClient client = restClientBuilder.build();
            String responseBody = client.get()
                    .uri(targetUrl)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + getSanitizedToken())
                    .retrieve()
                    .body(String.class);

            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode results = root.get("results");
            if (results != null && results.isArray() && !results.isEmpty()) {
                return objectMapper.treeToValue(results.get(0), MpCustomerResponse.class);
            }
            return null;
        } catch (Exception ex) {
            log.warn("[MercadoPagoRestClient] Búsqueda de cliente por email {} falló o no existe: {}", email, ex.getMessage());
            return null;
        }
    }

    /**
     * 6. Listar tarjetas de un cliente en Mercado Pago (GET /v1/customers/{customerId}/cards)
     */
    public List<MpCustomerResponse.MpCardResponse> getCustomerCards(String customerId, String arenaId, Long usuarioId) {
        String targetUrl = properties.getBaseUrl() + "/v1/customers/" + customerId + "/cards";
        try {
            RestClient client = restClientBuilder.build();
            String responseBody = client.get()
                    .uri(targetUrl)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + getSanitizedToken())
                    .retrieve()
                    .body(String.class);

            return objectMapper.readValue(responseBody, new TypeReference<List<MpCustomerResponse.MpCardResponse>>() {});
        } catch (Exception ex) {
            log.warn("[MercadoPagoRestClient] Error obteniendo tarjetas del cliente {}: {}", customerId, ex.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * 7. Guardar tarjeta en cliente usando Token (POST /v1/customers/{customerId}/cards)
     */
    public MpCustomerResponse.MpCardResponse saveCustomerCard(String customerId, String token, String arenaId, Long usuarioId) {
        String targetUrl = properties.getBaseUrl() + "/v1/customers/" + customerId + "/cards";
        LocalDateTime startTime = LocalDateTime.now();
        String reqJson = "{\"token\":\"" + token + "\"}";

        try {
            RestClient client = restClientBuilder.build();
            String responseBody = client.post()
                    .uri(targetUrl)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + getSanitizedToken())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(reqJson)
                    .retrieve()
                    .body(String.class);

            auditService.logExternalCall(IntegrationAuditService.SERVICIO_MERCADOPAGO, arenaId, usuarioId, "MercadoPago_SaveCard", reqJson, responseBody, startTime, LocalDateTime.now(), 200, "SUCCESS");
            return objectMapper.readValue(responseBody, MpCustomerResponse.MpCardResponse.class);
        } catch (HttpStatusCodeException ex) {
            String errBody = ex.getResponseBodyAsString();
            auditService.logExternalCall(IntegrationAuditService.SERVICIO_MERCADOPAGO, arenaId, usuarioId, "MercadoPago_SaveCard", reqJson, errBody, startTime, LocalDateTime.now(), ex.getStatusCode().value(), "FAILED");
            throw new MercadoPagoApiException("MP_SAVE_CARD_ERROR", "Error guardando tarjeta en Mercado Pago: " + errBody, ex);
        } catch (Exception ex) {
            throw new MercadoPagoApiException("MP_SAVE_CARD_EXCEPTION", "Excepción guardando tarjeta: " + ex.getMessage(), ex);
        }
    }

    /**
     * 8. Eliminar tarjeta de cliente (DELETE /v1/customers/{customerId}/cards/{cardId})
     */
    public void deleteCustomerCard(String customerId, String cardId, String arenaId, Long usuarioId) {
        String targetUrl = properties.getBaseUrl() + "/v1/customers/" + customerId + "/cards/" + cardId;
        try {
            RestClient client = restClientBuilder.build();
            client.delete()
                    .uri(targetUrl)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + getSanitizedToken())
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception ex) {
            log.warn("[MercadoPagoRestClient] Error eliminando tarjeta {} de cliente {}: {}", cardId, customerId, ex.getMessage());
        }
    }

    /**
     * 9. Crear Perfil de Pago (POST /v1/customers/{customerId}/payment-profiles)
     */
    public MpPaymentProfileResponse createPaymentProfile(String customerId, MpPaymentProfileRequest request, String arenaId, Long usuarioId) {
        String targetUrl = properties.getBaseUrl() + "/v1/customers/" + customerId + "/payment-profiles";
        LocalDateTime startTime = LocalDateTime.now();
        String reqJson = "";
        try {
            reqJson = objectMapper.writeValueAsString(request);
        } catch (Exception ignored) {}

        try {
            RestClient client = restClientBuilder.build();
            String responseBody = client.post()
                    .uri(targetUrl)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + getSanitizedToken())
                    .header("X-Idempotency-Key", UUID.randomUUID().toString())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(reqJson)
                    .retrieve()
                    .body(String.class);

            auditService.logExternalCall(IntegrationAuditService.SERVICIO_MERCADOPAGO, arenaId, usuarioId, "MercadoPago_CreatePaymentProfile", reqJson, responseBody, startTime, LocalDateTime.now(), 200, "SUCCESS");
            return objectMapper.readValue(responseBody, MpPaymentProfileResponse.class);
        } catch (HttpStatusCodeException ex) {
            String errBody = ex.getResponseBodyAsString();
            auditService.logExternalCall(IntegrationAuditService.SERVICIO_MERCADOPAGO, arenaId, usuarioId, "MercadoPago_CreatePaymentProfile", reqJson, errBody, startTime, LocalDateTime.now(), ex.getStatusCode().value(), "FAILED");
            throw new MercadoPagoApiException("MP_PROFILE_ERROR", "Error creando perfil de pago: " + errBody, ex);
        } catch (Exception ex) {
            throw new MercadoPagoApiException("MP_PROFILE_EXCEPTION", "Excepción creando perfil de pago: " + ex.getMessage(), ex);
        }
    }

    /**
     * 10. Listar Perfiles de Pago (GET /v1/customers/{customerId}/payment-profiles)
     */
    public List<MpPaymentProfileResponse> getPaymentProfiles(String customerId, String arenaId, Long usuarioId) {
        String targetUrl = properties.getBaseUrl() + "/v1/customers/" + customerId + "/payment-profiles";
        try {
            RestClient client = restClientBuilder.build();
            String responseBody = client.get()
                    .uri(targetUrl)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + getSanitizedToken())
                    .retrieve()
                    .body(String.class);

            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode arrayNode = root.isArray() ? root : (root.has("data") ? root.get("data") : null);
            if (arrayNode != null && arrayNode.isArray()) {
                return objectMapper.readValue(arrayNode.toString(), new TypeReference<List<MpPaymentProfileResponse>>() {});
            }
            return Collections.emptyList();
        } catch (Exception ex) {
            log.warn("[MercadoPagoRestClient] Error consultando perfiles de pago para {}: {}", customerId, ex.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * 11. Crear Card Token (POST /v1/card_tokens)
     */
    public MpCardTokenResponse createCardToken(MpCardTokenRequest request, String arenaId, Long usuarioId) {
        String targetUrl = properties.getBaseUrl() + "/v1/card_tokens";
        LocalDateTime startTime = LocalDateTime.now();
        String reqJson = "";
        try {
            reqJson = objectMapper.writeValueAsString(request);
        } catch (Exception ignored) {}

        try {
            RestClient client = restClientBuilder.build();
            String responseBody = client.post()
                    .uri(targetUrl)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + getSanitizedToken())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(reqJson)
                    .retrieve()
                    .body(String.class);

            auditService.logExternalCall(IntegrationAuditService.SERVICIO_MERCADOPAGO, arenaId, usuarioId, "MercadoPago_CreateCardToken", reqJson, responseBody, startTime, LocalDateTime.now(), 200, "SUCCESS");
            return objectMapper.readValue(responseBody, MpCardTokenResponse.class);
        } catch (HttpStatusCodeException ex) {
            String errBody = ex.getResponseBodyAsString();
            auditService.logExternalCall(IntegrationAuditService.SERVICIO_MERCADOPAGO, arenaId, usuarioId, "MercadoPago_CreateCardToken", reqJson, errBody, startTime, LocalDateTime.now(), ex.getStatusCode().value(), "FAILED");
            throw new MercadoPagoApiException("MP_CARD_TOKEN_ERROR", "Error creando token de tarjeta en Mercado Pago: " + errBody, ex);
        } catch (Exception ex) {
            throw new MercadoPagoApiException("MP_CARD_TOKEN_EXCEPTION", "Excepción creando token de tarjeta: " + ex.getMessage(), ex);
        }
    }
}


