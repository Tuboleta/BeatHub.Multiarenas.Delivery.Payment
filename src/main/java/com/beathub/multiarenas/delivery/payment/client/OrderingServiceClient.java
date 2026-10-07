package com.beathub.multiarenas.delivery.payment.client;

import com.beathub.multiarenas.delivery.payment.client.dto.PedidoClientResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;
import java.util.Optional;

@Component
@RequiredArgsConstructor
@Slf4j
public class OrderingServiceClient {

    private final RestClient.Builder restClientBuilder;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final com.beathub.multiarenas.delivery.payment.security.JwtTokenProvider jwtTokenProvider;

    @Value("${services.ordering.url:http://localhost:8082/api/v1/ordering}")
    private String orderingServiceUrl;

    private String resolveAuthToken(String authToken) {
        if (authToken != null && !authToken.isBlank()) {
            return authToken.startsWith("Bearer ") ? authToken : "Bearer " + authToken;
        }
        return "Bearer " + jwtTokenProvider.generarInternalServiceToken(null);
    }

    public void updateOrderStatus(Long pedidoId, Integer nuevoEstadoId, String observacion, String authToken) {
        String effectiveToken = resolveAuthToken(authToken);

        // Mapeo defensivo de estados de Payment a Ordering:
        // Payment 2 (Aprobado) -> Ordering 13 (PAGADO)
        // Payment 4 (Reversado) -> Ordering 20 (CANCELADO)
        // Payment 5 (Reembolsado/Rechazado) -> Ordering 21 (RECHAZADO)
        Integer orderingEstadoId = nuevoEstadoId;
        if (Integer.valueOf(2).equals(nuevoEstadoId)) {
            orderingEstadoId = 13;
        } else if (Integer.valueOf(4).equals(nuevoEstadoId)) {
            orderingEstadoId = 20;
        } else if (Integer.valueOf(5).equals(nuevoEstadoId)) {
            orderingEstadoId = 21;
        }

        // Si el estado es Pagado (13), intentar confirmar formalmente el pago en Ordering (TCPOS + Entrega)
        if (Integer.valueOf(13).equals(orderingEstadoId)) {
            try {
                String confirmUrl = orderingServiceUrl + "/pedidos/" + pedidoId + "/confirmar-pago";
                Map<String, Object> confirmBody = Map.of(
                        "tipoPago", "CREDIBANCO",
                        "autorizacion", "APROBADO_CREDIBANCO",
                        "terminal", "CREDIBANCO_WEB"
                );
                RestClient.RequestHeadersSpec<?> confirmReq = restClientBuilder.build()
                        .post()
                        .uri(confirmUrl)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", effectiveToken)
                        .body(confirmBody);

                confirmReq.retrieve().toBodilessEntity();
                log.info("Pago confirmado exitosamente en Ordering para pedido {} vía /confirmar-pago", pedidoId);
                return;
            } catch (Exception ex) {
                log.warn("No se pudo invocar /confirmar-pago para pedido {}: {}. Aplicando fallback a /estado", pedidoId, ex.getMessage());
            }
        }

        String url = orderingServiceUrl + "/pedidos/" + pedidoId + "/estado";

        Map<String, Object> body = Map.of(
                "nuevoEstadoId", orderingEstadoId,
                "estadoId", orderingEstadoId,
                "observacion", observacion != null ? observacion : "Estado actualizado por pasarela de pagos Credibanco"
        );

        try {
            RestClient.RequestHeadersSpec<?> req = restClientBuilder.build()
                    .patch()
                    .uri(url)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Authorization", effectiveToken)
                    .body(body);

            req.retrieve().toBodilessEntity();
            log.info("Notificado cambio de estado para pedido {} a estado {}", pedidoId, orderingEstadoId);
        } catch (Exception ex) {
            log.error("Error al notificar al microservicio Ordering para pedido {}: {}", pedidoId, ex.getMessage());
        }
    }

    public Optional<PedidoClientResponse> getPedido(Long pedidoId, String authToken) {
        String url = orderingServiceUrl + "/pedidos/" + pedidoId;

        try {
            RestClient.RequestHeadersSpec<?> req = restClientBuilder.build()
                    .get()
                    .uri(url)
                    .accept(MediaType.APPLICATION_JSON);

            req.header("Authorization", resolveAuthToken(authToken));

            String responseBody = req.retrieve().body(String.class);
            if (responseBody != null) {
                JsonNode root = objectMapper.readTree(responseBody);
                JsonNode dataNode = root.has("data") ? root.get("data") : root;
                PedidoClientResponse pedido = objectMapper.treeToValue(dataNode, PedidoClientResponse.class);
                return Optional.ofNullable(pedido);
            }
        } catch (Exception ex) {
            log.error("Error al consultar pedido {} en Ordering Service: {}", pedidoId, ex.getMessage());
        }
        return Optional.empty();
    }

    public void asociarGrupoPago(Long pedidoId, Long grupoPagoId, String authToken) {
        String url = orderingServiceUrl + "/pedidos/" + pedidoId + "/grupo-pago?grupoPagoId=" + grupoPagoId;

        try {
            RestClient.RequestHeadersSpec<?> req = restClientBuilder.build()
                    .patch()
                    .uri(url)
                    .accept(MediaType.APPLICATION_JSON)
                    .header("Authorization", resolveAuthToken(authToken));

            req.retrieve().toBodilessEntity();
            log.info("Asociado grupoPagoId {} al pedido {} en Ordering", grupoPagoId, pedidoId);
        } catch (Exception ex) {
            log.error("Error al asociar grupoPagoId {} al pedido {} en Ordering: {}", grupoPagoId, pedidoId, ex.getMessage());
        }
    }
}
