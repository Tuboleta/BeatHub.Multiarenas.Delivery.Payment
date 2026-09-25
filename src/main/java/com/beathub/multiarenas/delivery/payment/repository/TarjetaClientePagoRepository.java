package com.beathub.multiarenas.delivery.payment.repository;

import com.beathub.multiarenas.delivery.payment.entity.TarjetaClientePago;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TarjetaClientePagoRepository extends JpaRepository<TarjetaClientePago, Long> {
    List<TarjetaClientePago> findByClientePagoId(Long clientePagoId);
}
