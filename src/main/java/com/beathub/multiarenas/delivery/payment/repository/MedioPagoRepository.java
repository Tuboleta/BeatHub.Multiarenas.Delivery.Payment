package com.beathub.multiarenas.delivery.payment.repository;

import com.beathub.multiarenas.delivery.payment.entity.MedioPago;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface MedioPagoRepository extends JpaRepository<MedioPago, Integer> {
    Optional<MedioPago> findByNombre(String nombre);
}
