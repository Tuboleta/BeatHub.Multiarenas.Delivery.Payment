package com.beathub.multiarenas.delivery.payment.repository;

import com.beathub.multiarenas.delivery.payment.entity.PedidoPago;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PedidoPagoRepository extends JpaRepository<PedidoPago, Long> {
    Optional<PedidoPago> findByReferenciaPago(String referenciaPago);
    Optional<PedidoPago> findByCredibancoOrderId(String credibancoOrderId);
    Optional<PedidoPago> findByMdOrder(String mdOrder);
    List<PedidoPago> findByPedidoId(Long pedidoId);
    List<PedidoPago> findByUsuarioId(Long usuarioId);
    List<PedidoPago> findByGrupoPagoUsuario_GrupoPago_Id(Long grupoPagoId);
    List<PedidoPago> findByEstadoIdAndCredibancoOrderIdIsNotNull(Integer estadoId);
}
