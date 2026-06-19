package com.payment.platform.order.service.impl;

import cn.hutool.core.util.IdUtil;
import com.payment.platform.order.entity.Order;
import com.payment.platform.order.entity.RefundOrder;
import com.payment.platform.order.repository.OrderRepository;
import com.payment.platform.order.repository.RefundOrderRepository;
import com.payment.platform.order.service.RefundService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class RefundServiceImpl implements RefundService {

    private final RefundOrderRepository refundOrderRepository;
    private final OrderRepository orderRepository;

    @Override
    @Transactional
    public RefundOrder apply(String outRefundNo, String originOrderNo,
                              Long merchantId, BigDecimal refundAmount) {
        // 幂等检查
        return refundOrderRepository.findByOutRefundNoAndMerchantId(outRefundNo, merchantId)
                .orElseGet(() -> {
                    // 校验原订单
                    Order origin = orderRepository.findByOrderNo(originOrderNo)
                            .orElseThrow(() -> new RuntimeException("原订单不存在: " + originOrderNo));

                    RefundOrder refund = RefundOrder.builder()
                            .id(IdUtil.getSnowflake(3, 1).nextId())
                            .refundNo("RFN" + System.currentTimeMillis())
                            .outRefundNo(outRefundNo)
                            .originOrderNo(originOrderNo)
                            .merchantId(merchantId)
                            .refundAmount(refundAmount)
                            .status("REFUNDING")
                            .createTime(LocalDateTime.now())
                            .updateTime(LocalDateTime.now())
                            .build();
                    return refundOrderRepository.save(refund);
                });
    }
}
