package com.beathub.multiarenas.delivery.payment.config;

import com.beathub.multiarenas.delivery.payment.audit.AuditContextHolder;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskDecorator;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.web.client.RestClient;

@Configuration
public class RestClientConfig {

    private static final String CORRELATION_HEADER = "X-Correlation-Id";

    private ClientHttpRequestInterceptor correlationInterceptor() {
        return (request, body, execution) -> {
            String correlationId = AuditContextHolder.getCorrelationId();
            if (correlationId != null && !correlationId.isBlank()
                    && request.getHeaders().getFirst(CORRELATION_HEADER) == null) {
                request.getHeaders().add(CORRELATION_HEADER, correlationId);
            }
            return execution.execute(request, body);
        };
    }

    @Bean
    public RestClient.Builder restClientBuilder() {
        return RestClient.builder()
                .requestInterceptor(correlationInterceptor());
    }

    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper();
    }

    @Bean
    public TaskDecorator auditContextTaskDecorator() {
        return runnable -> {
            String correlationId = AuditContextHolder.getCorrelationId();
            String systemOrigin = AuditContextHolder.getSystemOrigin();
            return () -> {
                try {
                    AuditContextHolder.setCorrelationId(correlationId);
                    AuditContextHolder.setSystemOrigin(systemOrigin);
                    runnable.run();
                } finally {
                    AuditContextHolder.clear();
                }
            };
        };
    }
}
