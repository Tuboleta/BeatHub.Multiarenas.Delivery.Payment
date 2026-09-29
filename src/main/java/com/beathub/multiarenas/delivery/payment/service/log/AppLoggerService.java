package com.beathub.multiarenas.delivery.payment.service.log;

import com.beathub.multiarenas.delivery.payment.entity.log.ExceptionLogEntity;
import com.beathub.multiarenas.delivery.payment.repository.log.ExceptionLogRepository;
import com.beathub.multiarenas.delivery.payment.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.OffsetDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class AppLoggerService {

    private final ExceptionLogRepository exceptionLogRepository;

    private static final Integer MICROSERVICE_PAYMENT_ID = 4; // 4: PAYMENT en public.microservicio
    private static final Integer FLUJO_GENERAL_ID = 1;

    public void logException(Throwable ex, String detalle, String flujoId) {
        Long currentUserId = SecurityUtils.getCurrentUsuarioId().orElse(null);
        String currentArenaId = SecurityUtils.getCurrentArenaId().map(String::valueOf).orElse(null);
        logException(ex, detalle, flujoId, currentUserId, currentArenaId);
    }

    @Async
    public void logException(Throwable ex, String detalle, String flujoId, Long usuarioId, String arenaId) {
        try {
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            ex.printStackTrace(pw);
            String stackTrace = sw.toString();

            ExceptionLogEntity entity = ExceptionLogEntity.builder()
                    .microservicioId(MICROSERVICE_PAYMENT_ID)
                    .usuarioId(usuarioId)
                    .arenaId(arenaId)
                    .tipo(ex.getClass().getSimpleName())
                    .mensaje(ex.getMessage() != null ? ex.getMessage() : "Unknown exception")
                    .detalle(detalle)
                    .trace(stackTrace)
                    .flujoId(FLUJO_GENERAL_ID)
                    .creacionFecha(OffsetDateTime.now())
                    .creacionUsuario(usuarioId)
                    .build();

            exceptionLogRepository.save(entity);
        } catch (Exception loggingEx) {
            log.error("Failed to persist exception log into DB: {}", loggingEx.getMessage());
        }
    }
}
