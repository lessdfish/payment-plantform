package com.payment.platform.gateway.consumer;

import com.payment.platform.common.dto.event.PayProcessEvent;
import com.payment.platform.gateway.service.PayService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 后台支付结算消费者。异常抛给 RocketMQ，由 Broker 重投。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        name = "payment.worker.enabled",
        havingValue = "true",
        matchIfMissing = true)
@RocketMQMessageListener(
        topic = "pay-process",
        consumerGroup = "payment-process-consumer",
        consumeThreadNumber = 32,
        consumeThreadMax = 64,
        maxReconsumeTimes = 16)
public class PayProcessConsumer implements RocketMQListener<PayProcessEvent> {

    private final PayService payService;

    @Override
    public void onMessage(PayProcessEvent event) {
        log.debug("[PAY-PROCESS] 开始结算: outTradeNo={}", event.getOutTradeNo());
        payService.processAccepted(event.toRequest());
    }
}
