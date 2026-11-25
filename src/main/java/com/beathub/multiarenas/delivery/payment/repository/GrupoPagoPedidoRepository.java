package com.beathub.multiarenas.delivery.payment.repository;

import com.beathub.multiarenas.delivery.payment.entity.GrupoPagoPedido;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface GrupoPagoPedidoRepository extends JpaRepository<GrupoPagoPedido, Long> {

    List<GrupoPagoPedido> findByGrupoPagoId(Long grupoPagoId);

    Optional<GrupoPagoPedido> findByPedidoId(Long pedidoId);

    boolean existsByGrupoPagoIdAndPedidoId(Long grupoPagoId, Long pedidoId);
}
