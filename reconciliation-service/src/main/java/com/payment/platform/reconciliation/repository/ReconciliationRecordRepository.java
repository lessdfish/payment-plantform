package com.payment.platform.reconciliation.repository;

import com.payment.platform.reconciliation.entity.ReconciliationRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ReconciliationRecordRepository
        extends JpaRepository<ReconciliationRecord, Long> {

    Optional<ReconciliationRecord> findByOutTradeNo(String outTradeNo);
}
