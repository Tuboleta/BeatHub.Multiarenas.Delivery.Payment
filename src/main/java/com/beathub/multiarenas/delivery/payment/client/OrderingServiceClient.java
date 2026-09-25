package com.beathub.multiarenas.delivery.payment.client;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class OrderingServiceClient {

    private final RestClient.Builder restClientBuilder;

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
}
