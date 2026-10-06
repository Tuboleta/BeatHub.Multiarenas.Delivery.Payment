package com.beathub.multiarenas.delivery.payment.repository;

import com.beathub.multiarenas.delivery.payment.entity.ClientePago;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ClientePagoRepository extends JpaRepository<ClientePago, Long> {
    Optional<ClientePago> findByClienteReferencia(String clienteReferencia);
    List<ClientePago> findByArenaUsuarioId(Long arenaUsuarioId);
    List<ClientePago> findByArenaUsuarioIdAndEstadoId(Long arenaUsuarioId, Integer estadoId);
    Optional<ClientePago> findByArenaUsuarioIdAndBindingId(Long arenaUsuarioId, String bindingId);
    Optional<ClientePago> findByBindingId(String bindingId);
}
