package com.payment.platform.notification.consumer;

import com.payment.platform.common.dto.event.RefundSuccessEvent;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

/**
 * 退款通知消费者（当前骨架，Phase 4 完善）。
 */
@Slf4j
@Component
@RocketMQMessageListener(topic = "refund-notify", consumerGroup = "notify-refund-consumer")
public class RefundNotifyConsumer implements RocketMQListener<RefundSuccessEvent> {

    @Override
    public void onMessage(RefundSuccessEvent event) {
        log.info("[NOTIFY-CONSUMER] 收到退款事件: outRefundNo={}", event.getOutRefundNo());
    }
}
