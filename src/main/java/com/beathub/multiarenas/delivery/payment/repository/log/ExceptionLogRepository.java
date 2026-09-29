package com.beathub.multiarenas.delivery.payment.repository.log;

import com.beathub.multiarenas.delivery.payment.entity.log.ExceptionLogEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ExceptionLogRepository extends JpaRepository<ExceptionLogEntity, Long> {
}
