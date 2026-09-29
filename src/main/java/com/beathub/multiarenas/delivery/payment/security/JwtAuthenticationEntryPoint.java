package com.beathub.multiarenas.delivery.payment.security;

import com.beathub.multiarenas.delivery.payment.dto.response.ErrorResponse;
import com.beathub.multiarenas.delivery.payment.service.log.AppLoggerService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.LocalDateTime;

@Slf4j
@Component
public class JwtAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;
    private final AppLoggerService appLoggerService;

    public JwtAuthenticationEntryPoint(AppLoggerService appLoggerService) {
        this.appLoggerService = appLoggerService;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
    }

    @Override
    public void commence(HttpServletRequest request,
                         HttpServletResponse response,
                         AuthenticationException authException) throws IOException, ServletException {
        log.error("Acceso no autorizado al recurso {}: {}", request.getRequestURI(), authException.getMessage());

        String arenaId = request.getHeader("X-Arena-Id");
        appLoggerService.logException(authException, "Error de autenticación 401 en Payment: " + request.getRequestURI(), "AUTH_ENTRY_POINT", null, arenaId);

        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setStatus(HttpStatus.UNAUTHORIZED.value());

        ErrorResponse errorResponse = ErrorResponse.builder()
                .success(false)
                .status(HttpStatus.UNAUTHORIZED.value())
                .error(HttpStatus.UNAUTHORIZED.getReasonPhrase())
                .message("No autorizado: Token ausente, inválido o expirado")
                .path(request.getRequestURI())
                .timestamp(LocalDateTime.now())
                .build();

        objectMapper.writeValue(response.getOutputStream(), errorResponse);
    }
}
