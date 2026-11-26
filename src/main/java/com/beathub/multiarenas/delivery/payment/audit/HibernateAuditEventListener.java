package com.beathub.multiarenas.delivery.payment.audit;

import com.beathub.multiarenas.delivery.payment.security.SecurityUtils;
import com.beathub.multiarenas.delivery.payment.security.UserPrincipal;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.Hibernate;
import org.hibernate.collection.spi.PersistentCollection;
import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.event.spi.*;
import org.hibernate.persister.entity.EntityPersister;
import org.hibernate.proxy.HibernateProxy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.Types;
import java.util.*;

/**
 * Listener global de Hibernate que registra automáticamente INSERT, UPDATE y DELETE
 * en public.log_auditoria dentro de la misma transacción.
 */
@Slf4j
@Component
public class HibernateAuditEventListener implements PostInsertEventListener, PostUpdateEventListener, PostDeleteEventListener {

    private static final String INSERT_AUDIT_SQL = """
            INSERT INTO public.log_auditoria (
                microservicio_id, arena_id, arena_nombre, entidad, registro_id, accion,
                usuario_id, usuario_nombre, usuario_rol, cambios, correlation_id
            ) VALUES (
                ?, ?, (SELECT nombre FROM public.arena WHERE id = ?), ?, ?, ?,
                ?, ?, ?, CAST(? AS jsonb), ?
            )
            """;

    private static final Set<String> EXCLUDED_TABLES = Set.of(
            "log_auditoria",
            "log_gateway",
            "log_servicios",
            "exception_log"
    );

    private static final Set<String> IGNORED_DIFF_ONLY_FIELDS = Set.of(
            "actualizacionFecha",
            "actualizacionUsuario",
            "updatedAt",
            "updatedBy"
    );

    private static final List<String> SENSITIVE_KEYWORDS = List.of(
            "password", "secret", "token", "clave", "pin", "cvv", "apikey", "api_key"
    );

    private final ObjectMapper objectMapper;

    @Value("${app.audit.enabled:true}")
    private boolean auditEnabled;

    @Value("${app.audit.microservicio-id:4}")
    private Integer microservicioId;

    public HibernateAuditEventListener() {
        this.objectMapper = new ObjectMapper();
        this.objectMapper.findAndRegisterModules();
        this.objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    @Override
    public void onPostInsert(PostInsertEvent event) {
        if (!shouldAudit(event.getEntity())) {
            return;
        }
        String entidad = resolveTableName(event.getEntity());
        if (EXCLUDED_TABLES.contains(entidad)) {
            return;
        }
        String[] propertyNames = event.getPersister().getPropertyNames();
        Object[] state = event.getState();

        Map<String, Object> cambios = buildSnapshot(propertyNames, state);
        if (cambios.isEmpty()) {
            return;
        }

        Long arenaId = resolveArenaId(entidad, event.getId(), propertyNames, state);
        persistAudit(event.getSession(), entidad, event.getId(), "INSERT", arenaId, cambios);
    }

    @Override
    public void onPostUpdate(PostUpdateEvent event) {
        if (!shouldAudit(event.getEntity())) {
            return;
        }
        String entidad = resolveTableName(event.getEntity());
        if (EXCLUDED_TABLES.contains(entidad)) {
            return;
        }
        String[] propertyNames = event.getPersister().getPropertyNames();
        Object[] oldState = event.getOldState();
        Object[] newState = event.getState();
        int[] dirtyProperties = event.getDirtyProperties();

        Map<String, Object> cambios = buildDiff(propertyNames, oldState, newState, dirtyProperties);
        if (cambios.isEmpty()) {
            return;
        }

        Long arenaId = resolveArenaId(entidad, event.getId(), propertyNames, newState);
        if (arenaId == null && oldState != null) {
            arenaId = resolveArenaId(entidad, event.getId(), propertyNames, oldState);
        }
        persistAudit(event.getSession(), entidad, event.getId(), "UPDATE", arenaId, cambios);
    }

    @Override
    public void onPostDelete(PostDeleteEvent event) {
        if (!shouldAudit(event.getEntity())) {
            return;
        }
        String entidad = resolveTableName(event.getEntity());
        if (EXCLUDED_TABLES.contains(entidad)) {
            return;
        }
        String[] propertyNames = event.getPersister().getPropertyNames();
        Object[] deletedState = event.getDeletedState();

        Map<String, Object> cambios = buildSnapshot(propertyNames, deletedState);
        Long arenaId = resolveArenaId(entidad, event.getId(), propertyNames, deletedState);
        persistAudit(event.getSession(), entidad, event.getId(), "DELETE", arenaId, cambios);
    }

    @Override
    public boolean requiresPostCommitHandling(EntityPersister persister) {
        return false;
    }

    private boolean shouldAudit(Object entity) {
        return auditEnabled && !AuditContextHolder.isSkipAudit() && entity != null;
    }

    Map<String, Object> buildSnapshot(String[] propertyNames, Object[] state) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        if (propertyNames == null || state == null) {
            return snapshot;
        }
        for (int i = 0; i < propertyNames.length && i < state.length; i++) {
            Object raw = state[i];
            if (isSkippedCollection(raw)) {
                continue;
            }
            String prop = propertyNames[i];
            snapshot.put(prop, normalizeValue(prop, raw));
        }
        return snapshot;
    }

    Map<String, Object> buildDiff(String[] propertyNames, Object[] oldState, Object[] newState, int[] dirtyProperties) {
        Map<String, Object> diff = new LinkedHashMap<>();
        if (propertyNames == null || newState == null) {
            return diff;
        }

        if (oldState == null) {
            return buildSnapshot(propertyNames, newState);
        }

        if (dirtyProperties != null && dirtyProperties.length > 0) {
            for (int idx : dirtyProperties) {
                if (idx >= 0 && idx < propertyNames.length && idx < oldState.length && idx < newState.length) {
                    addDiffIfChanged(diff, propertyNames[idx], oldState[idx], newState[idx]);
                }
            }
        } else {
            for (int i = 0; i < propertyNames.length && i < oldState.length && i < newState.length; i++) {
                addDiffIfChanged(diff, propertyNames[i], oldState[i], newState[i]);
            }
        }

        if (!diff.isEmpty() && IGNORED_DIFF_ONLY_FIELDS.containsAll(diff.keySet())) {
            return Collections.emptyMap();
        }
        return diff;
    }

    private void addDiffIfChanged(Map<String, Object> diff, String prop, Object rawOld, Object rawNew) {
        if (isSkippedCollection(rawOld) || isSkippedCollection(rawNew)) {
            return;
        }
        Object normalizedOld = normalizeValue(prop, rawOld);
        Object normalizedNew = normalizeValue(prop, rawNew);
        if (areEqual(normalizedOld, normalizedNew)) {
            return;
        }
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("antes", normalizedOld);
        entry.put("despues", normalizedNew);
        diff.put(prop, entry);
    }

    private boolean isSkippedCollection(Object value) {
        return value instanceof PersistentCollection || value instanceof Collection<?>;
    }

    private Object normalizeValue(String propertyName, Object value) {
        if (value == null) {
            return null;
        }
        if (isSensitiveProperty(propertyName)) {
            return "***";
        }
        if (value instanceof HibernateProxy proxy) {
            return proxy.getHibernateLazyInitializer().getIdentifier();
        }
        if (value.getClass().isAnnotationPresent(Entity.class)) {
            return extractEntityId(value);
        }
        if (value instanceof Enum<?> enumVal) {
            return enumVal.name();
        }
        return value;
    }

    private boolean isSensitiveProperty(String propertyName) {
        if (propertyName == null) {
            return false;
        }
        String lower = propertyName.toLowerCase(Locale.ROOT);
        for (String keyword : SENSITIVE_KEYWORDS) {
            if (lower.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private boolean areEqual(Object a, Object b) {
        if (a == b) {
            return true;
        }
        if (a == null || b == null) {
            return false;
        }
        if (a instanceof BigDecimal bdA && b instanceof BigDecimal bdB) {
            return bdA.compareTo(bdB) == 0;
        }
        return Objects.equals(a, b);
    }

    String resolveTableName(Object entity) {
        Class<?> clazz = Hibernate.getClass(entity);
        Table table = clazz.getAnnotation(Table.class);
        if (table != null && table.name() != null && !table.name().isBlank()) {
            return table.name().toLowerCase(Locale.ROOT);
        }
        return clazz.getSimpleName().replaceAll("([a-z])([A-Z]+)", "$1_$2").toLowerCase(Locale.ROOT);
    }

    Long resolveArenaId(String entidad, Object id, String[] propertyNames, Object[] state) {
        if ("arena".equals(entidad) && id instanceof Number num) {
            return num.longValue();
        }
        if (propertyNames != null && state != null) {
            for (int i = 0; i < propertyNames.length && i < state.length; i++) {
                String prop = propertyNames[i];
                Object val = state[i];
                if (val == null) {
                    continue;
                }
                if ("arenaId".equals(prop) && val instanceof Number num) {
                    return num.longValue();
                }
                if ("arena".equals(prop)) {
                    Object extracted = normalizeValue(prop, val);
                    if (extracted instanceof Number num) {
                        return num.longValue();
                    }
                }
            }
        }
        return SecurityUtils.getCurrentArenaId().orElse(null);
    }

    private Object extractEntityId(Object entity) {
        try {
            Method getId = entity.getClass().getMethod("getId");
            return getId.invoke(entity);
        } catch (Exception ignored) {
            return entity.toString();
        }
    }

    private void persistAudit(SharedSessionContractImplementor session,
                              String entidad,
                              Object recordId,
                              String accion,
                              Long arenaId,
                              Map<String, Object> cambios) {
        String registroId = recordId != null ? String.valueOf(recordId) : "UNKNOWN";
        Optional<UserPrincipal> principalOpt = SecurityUtils.getCurrentUserPrincipal();

        Long usuarioId = principalOpt.map(UserPrincipal::getUsuarioId).orElse(null);
        String usuarioNombre = resolveUsuarioNombre(principalOpt);
        String usuarioRol = resolveUsuarioRol(principalOpt);
        String correlationId = AuditContextHolder.getCorrelationId();

        String cambiosJson;
        try {
            cambiosJson = objectMapper.writeValueAsString(cambios);
        } catch (Exception ex) {
            log.warn("No se pudo serializar JSON de auditoría para {}#{}: {}", entidad, registroId, ex.getMessage());
            return;
        }

        session.doWork(connection -> {
            try (PreparedStatement ps = connection.prepareStatement(INSERT_AUDIT_SQL)) {
                if (microservicioId != null) {
                    ps.setInt(1, microservicioId);
                } else {
                    ps.setNull(1, Types.INTEGER);
                }

                if (arenaId != null) {
                    ps.setLong(2, arenaId);
                    ps.setLong(3, arenaId);
                } else {
                    ps.setNull(2, Types.BIGINT);
                    ps.setNull(3, Types.BIGINT);
                }

                ps.setString(4, entidad);
                ps.setString(5, registroId);
                ps.setString(6, accion);

                if (usuarioId != null) {
                    ps.setLong(7, usuarioId);
                } else {
                    ps.setNull(7, Types.BIGINT);
                }

                ps.setString(8, usuarioNombre);
                if (usuarioRol != null) {
                    ps.setString(9, usuarioRol);
                } else {
                    ps.setNull(9, Types.VARCHAR);
                }

                ps.setString(10, cambiosJson);

                if (correlationId != null) {
                    ps.setString(11, correlationId);
                } else {
                    ps.setNull(11, Types.VARCHAR);
                }

                ps.executeUpdate();
            }
        });
    }

    private String resolveUsuarioNombre(Optional<UserPrincipal> principalOpt) {
        if (principalOpt.isPresent()) {
            UserPrincipal p = principalOpt.get();
            if (p.getUsername() != null && !p.getUsername().isBlank()) {
                return p.getUsername();
            }
            if (p.getEmail() != null && !p.getEmail().isBlank()) {
                return p.getEmail();
            }
            if (p.getUsuarioId() != null) {
                return "USER_" + p.getUsuarioId();
            }
        }
        String systemOrigin = AuditContextHolder.getSystemOrigin();
        return (systemOrigin != null && !systemOrigin.isBlank()) ? systemOrigin : "SYSTEM";
    }

    private String resolveUsuarioRol(Optional<UserPrincipal> principalOpt) {
        return principalOpt
                .map(p -> p.getAuthorities().stream()
                        .map(a -> a.getAuthority().replaceFirst("^ROLE_", ""))
                        .findFirst()
                        .orElse(null))
                .orElse(null);
    }
}
