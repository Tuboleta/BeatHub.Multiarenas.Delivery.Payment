package com.beathub.multiarenas.delivery.payment.service;

import com.beathub.multiarenas.delivery.payment.entity.LogServicio;
import com.beathub.multiarenas.delivery.payment.repository.LogServicioRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class IntegrationAuditService {

    public static final int SERVICIO_TCPOS = 1;
    public static final int SERVICIO_CREDIBANCO = 2;
    public static final int SERVICIO_ADS_SSO = 3;
    public static final int SERVICIO_MERCADOPAGO = 4;

    private static final int MICROSERVICIO_PAYMENT = 4;

    private final LogServicioRepository logServicioRepository;
    private final ObjectMapper objectMapper;

    public void logExternalCall(
            Integer servicioIntegracionId,
            String arenaId,
            Long usuarioId,
            String operacion,
            String request,
            String response,
            LocalDateTime fechaProceso,
            LocalDateTime fechaRespuesta,
            Integer httpCode,
            String resultado
    ) {
        try {
            long duracionMs = (fechaProceso != null && fechaRespuesta != null)
                    ? Duration.between(fechaProceso, fechaRespuesta).toMillis()
                    : 0L;

            LogServicio logEntity = LogServicio.builder()
                    .arenaId(arenaId)
                    .usuarioId(usuarioId)
                    .servicioIntegracionId(servicioIntegracionId != null ? servicioIntegracionId : SERVICIO_CREDIBANCO)
                    .microservicioId(MICROSERVICIO_PAYMENT)
                    .operacion(operacion)
                    .request(request)
                    .response(response)
                    .fechaProceso(fechaProceso != null ? fechaProceso : LocalDateTime.now())
                    .fechaRespuesta(fechaRespuesta != null ? fechaRespuesta : LocalDateTime.now())
                    .duracionMs(duracionMs)
                    .httpCode(httpCode)
                    .resultado(resultado)
                    .estadoId(1)
                    .creacionUsuario(usuarioId)
                    .build();

            logServicioRepository.save(logEntity);
        } catch (Exception ex) {
            log.error("Error guardando traza en public.log_servicios: {}", ex.getMessage());
        }
    }

    public void logExternalCall(
            String arenaId,
            Long usuarioId,
            String operacion,
            String request,
            String response,
            LocalDateTime fechaProceso,
            LocalDateTime fechaRespuesta,
            Integer httpCode,
            String resultado
    ) {
        logExternalCall(SERVICIO_CREDIBANCO, arenaId, usuarioId, operacion, request, response, fechaProceso, fechaRespuesta, httpCode, resultado);
    }

    public void logWebhook(
            Integer servicioIntegracionId,
            String arenaId,
            Long usuarioId,
            String operacion,
            Object requestPayload,
            Object responsePayload,
            LocalDateTime fechaProceso,
            LocalDateTime fechaRespuesta,
            Integer httpCode,
            String resultado
    ) {
        String reqStr = serializeToString(requestPayload);
        String resStr = serializeToString(responsePayload);
        logExternalCall(servicioIntegracionId, arenaId, usuarioId, operacion, reqStr, resStr, fechaProceso, fechaRespuesta, httpCode, resultado);
    }

    private String serializeToString(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof String str) {
            return str;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return String.valueOf(value);
        }
    }
}
