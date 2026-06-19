package com.payment.platform.order.repository;

import com.payment.platform.order.entity.Order;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {
    Optional<Order> findByOrderNo(String orderNo);
    Optional<Order> findByOutTradeNoAndMerchantId(String outTradeNo, Long merchantId);
}
