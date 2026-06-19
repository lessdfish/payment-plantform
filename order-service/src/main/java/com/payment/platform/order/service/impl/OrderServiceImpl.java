package com.payment.platform.order.service.impl;

import cn.hutool.core.util.IdUtil;
import com.payment.platform.order.entity.Order;
import com.payment.platform.order.repository.OrderRepository;
import com.payment.platform.order.service.OrderService;
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

    @Override
    public Order getByOrderNo(String orderNo) {
        return orderRepository.findByOrderNo(orderNo)
                .orElseThrow(() -> new EntityNotFoundException("订单不存在: " + orderNo));
    }

    @Override
    @Transactional
    public void updateStatus(String orderNo, String newStatus) {
        Order order = getByOrderNo(orderNo);
        order.setStatus(newStatus);
        order.setUpdateTime(LocalDateTime.now());
        orderRepository.save(order);
    }

    @Override
    @Transactional
    public Order create(String orderNo, String outTradeNo, Long merchantId,
                         BigDecimal amount, String channelOrderNo) {
        Order order = Order.builder()
                .id(IdUtil.getSnowflake(3, 1).nextId())
                .orderNo(orderNo)
                .outTradeNo(outTradeNo)
                .merchantId(merchantId)
                .amount(amount)
                .status("PAID")
                .channelOrderNo(channelOrderNo)
                .createTime(LocalDateTime.now())
                .updateTime(LocalDateTime.now())
                .build();
        return orderRepository.save(order);
    }
}
