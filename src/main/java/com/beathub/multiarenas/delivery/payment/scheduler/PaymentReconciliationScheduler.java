package com.beathub.multiarenas.delivery.payment.scheduler;

import com.beathub.multiarenas.delivery.payment.entity.PedidoPago;
import com.beathub.multiarenas.delivery.payment.repository.PedidoPagoRepository;
import com.beathub.multiarenas.delivery.payment.service.IntegrationAuditService;
import com.beathub.multiarenas.delivery.payment.service.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Sonda automática de reconciliación de pagos en segundo plano.
 * Deshabilitada por defecto para evitar consultas repetitivas innecesarias.
 * Solo reconcilia las órdenes de la pasarela activa configurada.
 */
@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "payment.sonda.enabled", havingValue = "true", matchIfMissing = false)
public class PaymentReconciliationScheduler {

    private final PedidoPagoRepository pedidoPagoRepository;
    private final PaymentService paymentService;

    @Value("${payment.default-gateway:MERCADOPAGO}")
    private String defaultGateway;

    @Value("${payment.sonda.max-age-minutes:30}")
    private int maxAgeMinutes;

    @Scheduled(fixedDelayString = "${payment.sonda.fixed-delay-ms:300000}", initialDelay = 60000)
    public void reconcilePendingPayments() {
        boolean isMercadoPago = "MERCADOPAGO".equalsIgnoreCase(defaultGateway);
        boolean isCredibanco = "CREDIBANCO".equalsIgnoreCase(defaultGateway);
        LocalDateTime cutoffTime = LocalDateTime.now().minusMinutes(maxAgeMinutes);

        // 1. Reconciliar órdenes de Mercado Pago sólo si MP está activo
        if (isMercadoPago || !isCredibanco) {
            List<PedidoPago> iniciadosMp = pedidoPagoRepository.findByEstadoIdAndMpOrderIdIsNotNull(1);
            for (PedidoPago pago : iniciadosMp) {
                try {
                    // Ignorar transacciones explícitas de Credibanco
                    if (Integer.valueOf(IntegrationAuditService.SERVICIO_CREDIBANCO).equals(pago.getPasarelaId())) {
                        continue;
                    }
                    if (pago.getCreacionFecha() != null && pago.getCreacionFecha().isBefore(cutoffTime)) {
                        continue;
                    }
                    log.info("Sonda de Pagos: Reconciliando estado MP de transacción #{} (referencia: {}, mpOrderId: {})",
                            pago.getId(), pago.getReferenciaPago(), pago.getMpOrderId());

                    paymentService.queryPaymentStatus(pago.getId(), pago.getArenaId(), pago.getUsuarioId());
                } catch (Exception ex) {
                    log.warn("Sonda de Pagos: Error al reconciliar pago MP #{}: {}", pago.getId(), ex.getMessage());
                }
            }
        }

        // 2. Reconciliar órdenes de Credibanco sólo si Credibanco está activo
        if (isCredibanco) {
            List<PedidoPago> iniciadosCredibanco = pedidoPagoRepository.findByEstadoIdAndCredibancoOrderIdIsNotNull(1);
            for (PedidoPago pago : iniciadosCredibanco) {
                try {
                    // Ignorar transacciones explícitas de Mercado Pago
                    if (Integer.valueOf(IntegrationAuditService.SERVICIO_MERCADOPAGO).equals(pago.getPasarelaId())) {
                        continue;
                    }
                    if (pago.getCreacionFecha() != null && pago.getCreacionFecha().isBefore(cutoffTime)) {
                        continue;
                    }
                    log.info("Sonda de Pagos: Reconciliando estado Credibanco de transacción #{} (referencia: {}, credibancoOrderId: {})",
                            pago.getId(), pago.getReferenciaPago(), pago.getCredibancoOrderId());

                    paymentService.queryPaymentStatus(pago.getId(), pago.getArenaId(), pago.getUsuarioId());
                } catch (Exception ex) {
                    log.warn("Sonda de Pagos: Error al reconciliar pago Credibanco #{}: {}", pago.getId(), ex.getMessage());
                }
            }
        }
    }
}
