package com.payment.platform.order.service.impl;

import cn.hutool.core.util.IdUtil;
import com.payment.platform.order.entity.Order;
import com.payment.platform.order.entity.RefundOrder;
import com.payment.platform.order.repository.OrderRepository;
import com.payment.platform.order.repository.RefundOrderRepository;
import com.payment.platform.order.client.AccountClient;
import com.payment.platform.order.producer.RefundProducer;
import com.payment.platform.order.service.RefundService;
import com.payment.platform.common.dto.event.RefundSuccessEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Service
@Slf4j
@RequiredArgsConstructor
public class RefundServiceImpl implements RefundService {

    private final RefundOrderRepository refundOrderRepository;
    private final OrderRepository orderRepository;
    private final AccountClient accountClient;
    private final RefundProducer refundProducer;

    @Override
    public RefundOrder apply(String outRefundNo, String originOrderNo,
                              Long merchantId, BigDecimal refundAmount) {
        Order origin = orderRepository.findByOrderNo(originOrderNo)
                .orElseThrow(() -> new RuntimeException("原订单不存在: " + originOrderNo));
        if (!origin.getMerchantId().equals(merchantId)) {
            throw new IllegalArgumentException("原订单不属于当前商户");
        }
        if (!"PAID".equals(origin.getStatus())
                && !"SETTLED".equals(origin.getStatus())) {
            throw new IllegalStateException("当前订单状态不可退款: " + origin.getStatus());
        }
        if (refundAmount.signum() <= 0) {
            throw new IllegalArgumentException("退款金额必须大于 0");
        }

        RefundOrder refund = refundOrderRepository
                .findByOutRefundNoAndMerchantId(outRefundNo, merchantId)
                .orElse(null);
        if (refund != null && ("REFUNDED".equals(refund.getStatus())
                || "FAILED".equals(refund.getStatus()))) {
            return refund;
        }

        BigDecimal refunded = refundOrderRepository
                .findByOriginOrderNoAndMerchantId(originOrderNo, merchantId)
                .stream()
                .filter(item -> !item.getOutRefundNo().equals(outRefundNo))
                .filter(item -> !"FAILED".equals(item.getStatus()))
                .map(RefundOrder::getRefundAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (refunded.add(refundAmount).compareTo(origin.getAmount()) > 0) {
            throw new IllegalArgumentException("累计退款金额不能超过原订单金额");
        }

        if (refund == null) {
            refund = RefundOrder.builder()
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
            refundOrderRepository.saveAndFlush(refund);
        }

        try {
            accountClient.refund(merchantId, refundAmount, outRefundNo);
        } catch (Exception e) {
            refundOrderRepository.updateStatus(outRefundNo, merchantId,
                    "FAILED", LocalDateTime.now());
            throw new RuntimeException("退款处理失败", e);
        }

        refundOrderRepository.updateStatus(outRefundNo, merchantId,
                "REFUNDED", LocalDateTime.now());
        refund.setStatus("REFUNDED");
        refund.setUpdateTime(LocalDateTime.now());

        if (refunded.add(refundAmount).compareTo(origin.getAmount()) == 0) {
            orderRepository.updateStatus(origin.getOrderNo(), merchantId,
                    "REFUNDED", LocalDateTime.now());
        }

        try {
            refundProducer.sendRefundNotify(RefundSuccessEvent.builder()
                    .outRefundNo(outRefundNo)
                    .originOutTradeNo(origin.getOutTradeNo())
                    .merchantId(merchantId)
                    .refundAmount(refundAmount)
                    .notifyUrl(origin.getNotifyUrl())
                    .build());
        } catch (Exception e) {
            log.error("[REFUND] 退款已入账，通知消息发送失败: outRefundNo={}",
                    outRefundNo, e);
        }
        return refund;
    }
}
