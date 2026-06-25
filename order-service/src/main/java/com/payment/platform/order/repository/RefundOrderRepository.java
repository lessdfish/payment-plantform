package com.payment.platform.order.repository;

import com.payment.platform.order.entity.RefundOrder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.List;

@Repository
public interface RefundOrderRepository extends JpaRepository<RefundOrder, Long> {
    Optional<RefundOrder> findByOutRefundNoAndMerchantId(String outRefundNo, Long merchantId);
    List<RefundOrder> findByOriginOrderNoAndMerchantId(String originOrderNo, Long merchantId);

    @Modifying
    @org.springframework.transaction.annotation.Transactional
    @Query("UPDATE RefundOrder r SET r.status = :status, r.updateTime = :updateTime "
            + "WHERE r.outRefundNo = :outRefundNo AND r.merchantId = :merchantId")
    int updateStatus(String outRefundNo, Long merchantId, String status,
                     LocalDateTime updateTime);
}
