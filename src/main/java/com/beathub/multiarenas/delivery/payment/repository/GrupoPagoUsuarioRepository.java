package com.beathub.multiarenas.delivery.payment.repository;

import com.beathub.multiarenas.delivery.payment.entity.GrupoPagoUsuario;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface GrupoPagoUsuarioRepository extends JpaRepository<GrupoPagoUsuario, Long> {
    List<GrupoPagoUsuario> findByGrupoPagoId(Long grupoPagoId);
    List<GrupoPagoUsuario> findByGrupoPagoIdAndEstadoIdNot(Long grupoPagoId, Integer estadoId);
    List<GrupoPagoUsuario> findByUsuarioId(Long usuarioId);
    Optional<GrupoPagoUsuario> findByGrupoPagoIdAndUsuarioId(Long grupoPagoId, Long usuarioId);
    boolean existsByGrupoPagoIdAndUsuarioId(Long grupoPagoId, Long usuarioId);
}
