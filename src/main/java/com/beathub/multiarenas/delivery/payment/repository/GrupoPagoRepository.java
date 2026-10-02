package com.beathub.multiarenas.delivery.payment.repository;

import com.beathub.multiarenas.delivery.payment.entity.GrupoPago;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface GrupoPagoRepository extends JpaRepository<GrupoPago, Long> {
    Optional<GrupoPago> findByCodigoUnico(String codigoUnico);
    Optional<GrupoPago> findByPedidoId(Long pedidoId);
    Optional<GrupoPago> findByPedidoIdAndEstadoId(Long pedidoId, Integer estadoId);
    List<GrupoPago> findByArenaId(String arenaId);
    List<GrupoPago> findByCreacionUsuario(Long creacionUsuario);
}
