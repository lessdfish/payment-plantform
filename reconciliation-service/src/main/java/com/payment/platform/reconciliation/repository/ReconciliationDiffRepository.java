package com.payment.platform.reconciliation.repository;

import com.payment.platform.reconciliation.entity.ReconciliationDiff;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface ReconciliationDiffRepository extends JpaRepository<ReconciliationDiff, Long> {
    List<ReconciliationDiff> findByStatus(String status);
    boolean existsByOutTradeNo(String outTradeNo);
}
