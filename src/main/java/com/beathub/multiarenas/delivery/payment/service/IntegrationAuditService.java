package com.beathub.multiarenas.delivery.payment.service;

import com.beathub.multiarenas.delivery.payment.entity.LogServicio;
import com.beathub.multiarenas.delivery.payment.repository.LogServicioRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class IntegrationAuditService {

    private final LogServicioRepository logServicioRepository;

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
        try {
            long duracionMs = Duration.between(fechaProceso, fechaRespuesta).toMillis();

            LogServicio logEntity = LogServicio.builder()
                    .arenaId(arenaId)
                    .usuarioId(usuarioId)
                    .servicioIntegracionId(2) // CREDIBANCO
                    .microservicioId(4)       // PAYMENT
                    .operacion(operacion)
                    .request(request)
                    .response(response)
                    .fechaProceso(fechaProceso)
                    .fechaRespuesta(fechaRespuesta)
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
}
