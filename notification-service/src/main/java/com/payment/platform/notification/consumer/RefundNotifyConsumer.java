package com.payment.platform.notification.consumer;

import com.payment.platform.common.dto.event.RefundSuccessEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.payment.platform.notification.service.NotifyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

/**
 * 退款通知消费者（当前骨架，Phase 4 完善）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@RocketMQMessageListener(topic = "refund-notify", consumerGroup = "notify-refund-consumer")
public class RefundNotifyConsumer implements RocketMQListener<RefundSuccessEvent> {

    private final NotifyService notifyService;
    private final ObjectMapper objectMapper;

    @Override
    public void onMessage(RefundSuccessEvent event) {
        log.info("[NOTIFY-CONSUMER] 收到退款事件: outRefundNo={}", event.getOutRefundNo());
        try {
            notifyService.sendCallback(event.getOutRefundNo(), event.getMerchantId(),
                    event.getNotifyUrl(), objectMapper.writeValueAsString(event));
        } catch (Exception e) {
            throw new RuntimeException("退款回调处理失败", e);
        }
    }
}
