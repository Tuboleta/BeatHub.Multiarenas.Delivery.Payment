package com.beathub.multiarenas.delivery.payment.client;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

@Component
@Slf4j
public class NotificationServiceClient {

    private final RestClient restClient;
    private final String notificationUrl;

    public NotificationServiceClient(RestClient.Builder restClientBuilder,
                                   @Value("${services.notification.url:http://localhost:8085/api/v1/notifications}") String notificationUrl) {
        this.restClient = restClientBuilder.build();
        this.notificationUrl = notificationUrl;
    }

    @Async
    public void notificarInvitacionVaca(Long arenaId, Long usuarioId, String nombreInvitado, String emailInvitado,
                                       String telefonoInvitado, String liderNombre, String nombreGrupo,
                                       BigDecimal cuota, String urlVaca) {
        Map<String, Object> datos = new HashMap<>();
        datos.put("nombre", nombreInvitado != null ? nombreInvitado : "Amigo");
        datos.put("liderNombre", liderNombre != null ? liderNombre : "Un amigo");
        datos.put("nombreGrupo", nombreGrupo != null ? nombreGrupo : "Vaca de Pedido");
        datos.put("cuota", cuota != null ? cuota : BigDecimal.ZERO);
        datos.put("urlVaca", urlVaca != null ? urlVaca : "");

        NotificationEventPayload payload = NotificationEventPayload.builder()
                .eventId(UUID.randomUUID().toString())
                .arenaId(arenaId)
                .usuarioId(usuarioId)
                .tipoEventoId(3) // 3: VACA_INVITATION
                .tipoNotificacionIds(List.of(1, 2, 5)) // 1: PUSH, 2: EMAIL, 5: IN_APP
                .destinatario(DestinatarioPayload.builder()
                        .email(emailInvitado)
                        .nombre(nombreInvitado)
                        .telefono(telefonoInvitado)
                        .build())
                .datos(datos)
                .timestamp(Instant.now().toString())
                .build();

        enviarEvento(payload);
    }

    @Async
    public void notificarVacaCompletada(Long arenaId, Long usuarioId, Long pedidoId, String nombreGrupo, BigDecimal total) {
        Map<String, Object> datos = new HashMap<>();
        datos.put("pedidoId", pedidoId);
        datos.put("nombreGrupo", nombreGrupo != null ? nombreGrupo : "Vaca de Pedido");
        datos.put("total", total != null ? total : BigDecimal.ZERO);

        NotificationEventPayload payload = NotificationEventPayload.builder()
                .eventId(UUID.randomUUID().toString())
                .arenaId(arenaId)
                .usuarioId(usuarioId)
                .tipoEventoId(4) // 4: VACA_COMPLETE
                .tipoNotificacionIds(List.of(1, 2, 5)) // 1: PUSH, 2: EMAIL, 5: IN_APP
                .destinatario(DestinatarioPayload.builder().build())
                .datos(datos)
                .timestamp(Instant.now().toString())
                .build();

        enviarEvento(payload);
    }

    private void enviarEvento(NotificationEventPayload payload) {
        try {
            String targetEndpoint = notificationUrl.endsWith("/")
                    ? notificationUrl + "events"
                    : notificationUrl + "/events";

            restClient.post()
                    .uri(targetEndpoint)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .retrieve()
                    .toBodilessEntity();

            log.info("Evento de notificación de pago {} (Tipo: {}) despachado hacia {}",
                    payload.getEventId(), payload.getTipoEventoId(), targetEndpoint);
        } catch (Exception ex) {
            log.warn("Aviso: No se pudo despachar evento de notificación {} hacia {}: {}",
                    payload.getEventId(), notificationUrl, ex.getMessage());
        }
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class NotificationEventPayload implements Serializable {
        private String eventId;
        private Long arenaId;
        private Long usuarioId;
        private Integer tipoEventoId;
        private List<Integer> tipoNotificacionIds;
        private DestinatarioPayload destinatario;
        private Map<String, Object> datos;
        private String timestamp;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DestinatarioPayload implements Serializable {
        private String email;
        private String nombre;
        private String telefono;
    }
}
