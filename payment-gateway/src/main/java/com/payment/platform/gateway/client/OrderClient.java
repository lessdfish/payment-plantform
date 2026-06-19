package com.payment.platform.gateway.client;

import com.payment.platform.common.dto.event.PaySuccessEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * 订单客户端 — 通过 RocketMQ 异步发送支付成功事件。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderClient {

    private final RocketMQTemplate rocketMQTemplate;

    public void sendPaySuccessEvent(String outTradeNo, Long merchantId,
                                     BigDecimal amount, String channelOrderNo) {
        PaySuccessEvent event = PaySuccessEvent.builder()
                .outTradeNo(outTradeNo)
                .merchantId(merchantId)
                .amount(amount)
                .channelOrderNo(channelOrderNo)
                .paidTime(System.currentTimeMillis())
                .build();
        rocketMQTemplate.send("pay-success", MessageBuilder.withPayload(event).build());
        log.info("[ORDER-CLIENT] 发送支付成功事件: outTradeNo={}", outTradeNo);
    }
}
