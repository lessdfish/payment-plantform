package com.payment.platform.order.consumer;

import com.payment.platform.common.dto.event.PaySuccessEvent;
import com.payment.platform.order.repository.DirectSettledOrderWriter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

/**
 * 消费支付成功事件，创建订单。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@RocketMQMessageListener(topic = "pay-success",
        consumerGroup = "order-pay-success-consumer")
public class PaySuccessConsumer implements RocketMQListener<PaySuccessEvent> {

    private final DirectSettledOrderWriter orderWriter;

    @Override
    public void onMessage(PaySuccessEvent event) {
        log.info("[ORDER-CONSUMER] 收到支付成功事件: outTradeNo={}", event.getOutTradeNo());
        try {
            orderWriter.write(event);
            log.info("[ORDER-CONSUMER] 订单结算成功: outTradeNo={}",
                    event.getOutTradeNo());
        } catch (Exception e) {
            // RocketMQ 默认会重试消费
            log.error("[ORDER-CONSUMER] 订单创建失败: outTradeNo={}", event.getOutTradeNo(), e);
            throw e;
        }
    }
}
