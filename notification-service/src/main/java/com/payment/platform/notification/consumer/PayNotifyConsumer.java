package com.payment.platform.notification.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.payment.platform.common.dto.event.PaySuccessEvent;
import com.payment.platform.notification.service.NotifyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

/**
 * 支付成功通知消费者 — 消费 pay-success 事件，向商户发送 HTTP 回调。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@RocketMQMessageListener(topic = "pay-success", consumerGroup = "notify-pay-success-consumer")
public class PayNotifyConsumer implements RocketMQListener<PaySuccessEvent> {

    private final NotifyService notifyService;
    private final ObjectMapper objectMapper;

    @Override
    public void onMessage(PaySuccessEvent event) {
        log.info("[NOTIFY-CONSUMER] 收到支付成功事件: outTradeNo={}", event.getOutTradeNo());
        try {
            String body = objectMapper.writeValueAsString(event);
            notifyService.sendCallback(event.getOutTradeNo(), event.getMerchantId(),
                    event.getNotifyUrl(), body);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            log.error("[NOTIFY-CONSUMER] JSON序列化失败: outTradeNo={}", event.getOutTradeNo(), e);
            throw new RuntimeException(e);
        } catch (Exception e) {
            log.error("[NOTIFY-CONSUMER] 回调处理失败: outTradeNo={}", event.getOutTradeNo(), e);
            throw new RuntimeException(e);
        }
    }
}
