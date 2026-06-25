package com.payment.platform.order.service.impl;

import cn.hutool.core.util.IdUtil;
import com.payment.platform.order.entity.Order;
import com.payment.platform.order.repository.OrderRepository;
import com.payment.platform.order.service.OrderService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {

    private final OrderRepository orderRepository;
    private final EntityManager entityManager;

    @Override
    public Order getByOutTradeNo(String outTradeNo, Long merchantId) {
        return orderRepository.findByOutTradeNoAndMerchantId(outTradeNo, merchantId)
                .orElseThrow(() -> new EntityNotFoundException("订单不存在: " + outTradeNo));
    }

    @Override
    public Order getByOrderNo(String orderNo) {
        return orderRepository.findByOrderNo(orderNo)
                .orElseThrow(() -> new EntityNotFoundException("订单不存在: " + orderNo));
    }

    @Override
    @Transactional
    public void updateStatus(String orderNo, Long merchantId, String newStatus) {
        orderRepository.updateStatus(
                orderNo, merchantId, newStatus, LocalDateTime.now());
    }

    @Override
    @Transactional
    public Order create(String orderNo, String outTradeNo, Long merchantId,
                         BigDecimal amount, String channelOrderNo, String notifyUrl) {
        Order existing = orderRepository.findByOutTradeNoAndMerchantId(outTradeNo, merchantId)
                .orElse(null);
        if (existing != null) {
            return existing;
        }

        Order order = Order.builder()
                .id(IdUtil.getSnowflake(3, 1).nextId())
                .orderNo(orderNo)
                .outTradeNo(outTradeNo)
                .merchantId(merchantId)
                .amount(amount)
                .status("PAID")
                .channelOrderNo(channelOrderNo)
                .notifyUrl(notifyUrl)
                .createTime(LocalDateTime.now())
                .updateTime(LocalDateTime.now())
                .build();
        entityManager.persist(order);
        entityManager.flush();
        return order;
    }
}
