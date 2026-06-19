package com.payment.platform.notification.repository;

import com.payment.platform.notification.entity.NotifyRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface NotifyRecordRepository extends JpaRepository<NotifyRecord, Long> {
    Optional<NotifyRecord> findByOutTradeNoAndStatus(String outTradeNo, String status);
}
