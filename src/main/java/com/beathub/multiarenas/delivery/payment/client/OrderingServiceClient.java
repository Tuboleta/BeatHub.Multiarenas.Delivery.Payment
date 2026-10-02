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

    @Value("${services.ordering.url:http://localhost:8082/api/v1/ordering}")
    private String orderingServiceUrl;

    public void updateOrderStatus(Long pedidoId, Integer nuevoEstadoId, String observacion, String authToken) {
        String url = orderingServiceUrl + "/pedidos/" + pedidoId + "/estado";

        Map<String, Object> body = Map.of(
                "estadoId", nuevoEstadoId,
                "observacion", observacion != null ? observacion : "Estado actualizado por pasarela de pagos Credibanco"
        );

        try {
            RestClient.RequestHeadersSpec<?> req = restClientBuilder.build()
                    .patch()
                    .uri(url)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body);

            if (authToken != null && !authToken.isEmpty()) {
                req.header("Authorization", authToken.startsWith("Bearer ") ? authToken : "Bearer " + authToken);
            }

            req.retrieve().toBodilessEntity();
            log.info("Notificado cambio de estado para pedido {} a estado {}", pedidoId, nuevoEstadoId);
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

            if (authToken != null && !authToken.isEmpty()) {
                req.header("Authorization", authToken.startsWith("Bearer ") ? authToken : "Bearer " + authToken);
            }

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
                    .accept(MediaType.APPLICATION_JSON);

            if (authToken != null && !authToken.isEmpty()) {
                req.header("Authorization", authToken.startsWith("Bearer ") ? authToken : "Bearer " + authToken);
            }

            req.retrieve().toBodilessEntity();
            log.info("Asociado grupoPagoId {} al pedido {} en Ordering", grupoPagoId, pedidoId);
        } catch (Exception ex) {
            log.error("Error al asociar grupoPagoId {} al pedido {} en Ordering: {}", grupoPagoId, pedidoId, ex.getMessage());
        }
    }
}
