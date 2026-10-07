package com.beathub.multiarenas.delivery.payment.client;

import com.beathub.multiarenas.delivery.payment.client.dto.UserClientResponse;
import com.beathub.multiarenas.delivery.payment.security.JwtTokenProvider;
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
import java.util.concurrent.ConcurrentHashMap;

@Component
@RequiredArgsConstructor
@Slf4j
public class AuthServiceClient {

    private final RestClient.Builder restClientBuilder;
    private final ObjectMapper objectMapper;
    private final JwtTokenProvider jwtTokenProvider;

    @Value("${services.auth.url:http://localhost:8080/api/v1/auth}")
    private String authServiceUrl;

    // Cache en memoria para evitar llamadas redundantes por cada sondeo
    private final Map<Long, UserClientResponse> userCache = new ConcurrentHashMap<>();

    public Optional<UserClientResponse> getUsuario(Long usuarioId) {
        if (usuarioId == null) {
            return Optional.empty();
        }

        if (userCache.containsKey(usuarioId)) {
            return Optional.of(userCache.get(usuarioId));
        }

        String url = authServiceUrl + "/usuarios/" + usuarioId;
        String token = "Bearer " + jwtTokenProvider.generarInternalServiceToken(null);

        try {
            String responseStr = restClientBuilder.build()
                    .get()
                    .uri(url)
                    .header("Authorization", token)
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(String.class);

            if (responseStr != null && !responseStr.isBlank()) {
                JsonNode root = objectMapper.readTree(responseStr);
                JsonNode dataNode = root.has("data") ? root.get("data") : root;
                UserClientResponse user = objectMapper.treeToValue(dataNode, UserClientResponse.class);
                if (user != null) {
                    userCache.put(usuarioId, user);
                    return Optional.of(user);
                }
            }
        } catch (Exception ex) {
            log.warn("No se pudo obtener información del usuario {} en Auth Service: {}", usuarioId, ex.getMessage());
        }

        return Optional.empty();
    }
}
