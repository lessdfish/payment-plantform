package com.payment.platform.order.producer;

import com.payment.platform.common.dto.event.RefundSuccessEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;

/**
 * 退款事件生产者。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RefundProducer {

    private final RocketMQTemplate rocketMQTemplate;

    public void sendRefundNotify(RefundSuccessEvent event) {
        rocketMQTemplate.send("refund-notify",
                MessageBuilder.withPayload(event).build());
        log.info("[REFUND-PRODUCER] 发送退款通知: outRefundNo={}", event.getOutRefundNo());
    }
}
