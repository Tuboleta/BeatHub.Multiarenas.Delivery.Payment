package com.beathub.multiarenas.delivery.payment.repository;

import com.beathub.multiarenas.delivery.payment.entity.TipoGrupoPago;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface TipoGrupoPagoRepository extends JpaRepository<TipoGrupoPago, Integer> {
    Optional<TipoGrupoPago> findByNombre(String nombre);
}
