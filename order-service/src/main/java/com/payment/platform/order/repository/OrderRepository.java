package com.payment.platform.order.repository;

import com.payment.platform.order.entity.Order;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {
    Optional<Order> findByOrderNo(String orderNo);
    Optional<Order> findByOutTradeNoAndMerchantId(String outTradeNo, Long merchantId);

    @Modifying
    @org.springframework.transaction.annotation.Transactional
    @Query("UPDATE Order o SET o.status = :status, o.updateTime = :updateTime "
            + "WHERE o.orderNo = :orderNo AND o.merchantId = :merchantId")
    int updateStatus(String orderNo, Long merchantId, String status,
                     LocalDateTime updateTime);
}
