package com.payment.platform.gateway.client;

import com.payment.platform.common.dto.event.PayProcessEvent;
import com.payment.platform.common.dto.request.PayRequest;
import lombok.RequiredArgsConstructor;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;

/**
 * 支付受理消息生产者。同步确认 Broker 接收后才向商户返回受理成功。
 */
@Component
@RequiredArgsConstructor
public class PayProcessProducer {

    private final RocketMQTemplate rocketMQTemplate;

    public void send(PayRequest request) {
        rocketMQTemplate.syncSend(
                "pay-process",
                MessageBuilder.withPayload(PayProcessEvent.from(request)).build(),
                3_000);
    }
}
