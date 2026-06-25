package com.payment.platform.gateway.client;

import com.payment.platform.common.dto.event.PaySuccessEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.producer.SendCallback;
import org.apache.rocketmq.client.producer.SendResult;
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
                                     BigDecimal amount, String channelOrderNo,
                                     String notifyUrl) {
        PaySuccessEvent event = PaySuccessEvent.builder()
                .outTradeNo(outTradeNo)
                .merchantId(merchantId)
                .amount(amount)
                .channelOrderNo(channelOrderNo)
                .notifyUrl(notifyUrl)
                .paidTime(System.currentTimeMillis())
                .build();
        rocketMQTemplate.asyncSend(
                "pay-success",
                MessageBuilder.withPayload(event).build(),
                new SendCallback() {
                    @Override
                    public void onSuccess(SendResult sendResult) {
                        log.debug("[ORDER-CLIENT] 支付成功事件已发送: outTradeNo={}, msgId={}",
                                outTradeNo, sendResult.getMsgId());
                    }

                    @Override
                    public void onException(Throwable throwable) {
                        log.error("[ORDER-CLIENT] 支付成功事件发送失败: outTradeNo={}",
                                outTradeNo, throwable);
                    }
                });
    }
}
