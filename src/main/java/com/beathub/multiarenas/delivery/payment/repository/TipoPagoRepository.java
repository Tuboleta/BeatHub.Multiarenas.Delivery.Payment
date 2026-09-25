package com.beathub.multiarenas.delivery.payment.repository;

import com.beathub.multiarenas.delivery.payment.entity.TipoPago;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface TipoPagoRepository extends JpaRepository<TipoPago, Integer> {
    Optional<TipoPago> findByNombre(String nombre);
}
