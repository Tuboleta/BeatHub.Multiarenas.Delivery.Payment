package com.beathub.multiarenas.delivery.payment.repository;

import com.beathub.multiarenas.delivery.payment.entity.LogServicio;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface LogServicioRepository extends JpaRepository<LogServicio, Long> {
    List<LogServicio> findByUsuarioIdOrderByFechaProcesoDesc(Long usuarioId);
}
