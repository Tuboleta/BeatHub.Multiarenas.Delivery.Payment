package com.beathub.multiarenas.delivery.payment.scheduler;

import com.beathub.multiarenas.delivery.payment.entity.PedidoPago;
import com.beathub.multiarenas.delivery.payment.repository.PedidoPagoRepository;
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
 * Consulta transacciones en estado INICIADO (1) con pasarela Credibanco
 * para sincronizar su estado final de manera resiliente, incluso si el
 * webhook no pudo ser entregado (ej. desarrollo local) o el usuario cerró el navegador.
 */
@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "payment.sonda.enabled", havingValue = "true", matchIfMissing = true)
public class PaymentReconciliationScheduler {

    private final PedidoPagoRepository pedidoPagoRepository;
    private final PaymentService paymentService;

    @Value("${payment.sonda.max-age-minutes:60}")
    private int maxAgeMinutes;

    @Scheduled(fixedDelayString = "${payment.sonda.fixed-delay-ms:20000}", initialDelay = 15000)
    public void reconcilePendingPayments() {
        List<PedidoPago> iniciados = pedidoPagoRepository.findByEstadoIdAndCredibancoOrderIdIsNotNull(1);
        if (iniciados.isEmpty()) {
            return;
        }

        LocalDateTime cutoffTime = LocalDateTime.now().minusMinutes(maxAgeMinutes);

        for (PedidoPago pago : iniciados) {
            try {
                if (pago.getCreacionFecha() != null && pago.getCreacionFecha().isBefore(cutoffTime)) {
                    continue;
                }
                log.info("Sonda de Pagos: Reconciliando estado de transacción #{} (referencia: {}, credibancoOrderId: {})",
                        pago.getId(), pago.getReferenciaPago(), pago.getCredibancoOrderId());

                paymentService.queryPaymentStatus(pago.getId(), pago.getArenaId(), pago.getUsuarioId());
            } catch (Exception ex) {
                log.warn("Sonda de Pagos: Error al reconciliar pago #{}: {}", pago.getId(), ex.getMessage());
            }
        }
    }
}
