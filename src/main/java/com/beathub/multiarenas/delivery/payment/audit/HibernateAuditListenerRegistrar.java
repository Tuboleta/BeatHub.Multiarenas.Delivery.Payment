package com.beathub.multiarenas.delivery.payment.audit;

import jakarta.annotation.PostConstruct;
import jakarta.persistence.EntityManagerFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.event.service.spi.EventListenerRegistry;
import org.hibernate.event.spi.EventType;
import org.hibernate.internal.SessionFactoryImpl;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class HibernateAuditListenerRegistrar {

    private final EntityManagerFactory entityManagerFactory;
    private final HibernateAuditEventListener auditEventListener;

    @Value("${app.audit.enabled:true}")
    private boolean auditEnabled;

    @PostConstruct
    public void registerListeners() {
        if (!auditEnabled) {
            log.info("Auditoría global de Hibernate desactivada (app.audit.enabled=false)");
            return;
        }
        SessionFactoryImpl sessionFactory = entityManagerFactory.unwrap(SessionFactoryImpl.class);
        EventListenerRegistry registry = sessionFactory.getServiceRegistry().getService(EventListenerRegistry.class);
        if (registry != null) {
            registry.getEventListenerGroup(EventType.POST_INSERT).appendListener(auditEventListener);
            registry.getEventListenerGroup(EventType.POST_UPDATE).appendListener(auditEventListener);
            registry.getEventListenerGroup(EventType.POST_DELETE).appendListener(auditEventListener);
            log.info("Auditoría global de Hibernate registrada (POST_INSERT, POST_UPDATE, POST_DELETE -> public.log_auditoria)");
        }
    }
}
