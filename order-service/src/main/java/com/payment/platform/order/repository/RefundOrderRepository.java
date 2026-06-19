package com.payment.platform.order.repository;

import com.payment.platform.order.entity.RefundOrder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface RefundOrderRepository extends JpaRepository<RefundOrder, Long> {
    Optional<RefundOrder> findByOutRefundNoAndMerchantId(String outRefundNo, Long merchantId);
}
