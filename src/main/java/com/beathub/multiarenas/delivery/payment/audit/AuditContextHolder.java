package com.beathub.multiarenas.delivery.payment.audit;

/**
 * Contexto por hilo para enriquecer el histórico de cambios (correlationId, origen del sistema o salto en cargas masivas).
 */
public final class AuditContextHolder {

    private static final ThreadLocal<String> CORRELATION_ID = new ThreadLocal<>();
    private static final ThreadLocal<String> SYSTEM_ORIGIN = new ThreadLocal<>();
    private static final ThreadLocal<Boolean> SKIP_AUDIT = new ThreadLocal<>();

    private AuditContextHolder() {
    }

    public static void setCorrelationId(String correlationId) {
        if (correlationId == null || correlationId.isBlank()) {
            CORRELATION_ID.remove();
        } else {
            CORRELATION_ID.set(correlationId.trim());
        }
    }

    public static String getCorrelationId() {
        return CORRELATION_ID.get();
    }

    public static void setSystemOrigin(String origin) {
        if (origin == null || origin.isBlank()) {
            SYSTEM_ORIGIN.remove();
        } else {
            SYSTEM_ORIGIN.set(origin.trim());
        }
    }

    public static String getSystemOrigin() {
        return SYSTEM_ORIGIN.get();
    }

    public static void setSkipAudit(boolean skip) {
        if (skip) {
            SKIP_AUDIT.set(Boolean.TRUE);
        } else {
            SKIP_AUDIT.remove();
        }
    }

    public static boolean isSkipAudit() {
        return Boolean.TRUE.equals(SKIP_AUDIT.get());
    }

    public static void clear() {
        CORRELATION_ID.remove();
        SYSTEM_ORIGIN.remove();
        SKIP_AUDIT.remove();
    }
}
