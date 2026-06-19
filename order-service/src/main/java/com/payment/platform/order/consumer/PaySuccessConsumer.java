package com.payment.platform.order.consumer;

import cn.hutool.core.util.IdUtil;
import com.payment.platform.common.dto.event.PaySuccessEvent;
import com.payment.platform.order.service.OrderService;
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

    private final OrderService orderService;

    @Override
    public void onMessage(PaySuccessEvent event) {
        log.info("[ORDER-CONSUMER] 收到支付成功事件: outTradeNo={}", event.getOutTradeNo());
        try {
            String orderNo = "ORD" + System.currentTimeMillis() + IdUtil.fastSimpleUUID().substring(0, 4);
            orderService.create(orderNo, event.getOutTradeNo(), event.getMerchantId(),
                    event.getAmount(), event.getChannelOrderNo());
            log.info("[ORDER-CONSUMER] 订单创建成功: orderNo={}", orderNo);
        } catch (Exception e) {
            // RocketMQ 默认会重试消费
            log.error("[ORDER-CONSUMER] 订单创建失败: outTradeNo={}", event.getOutTradeNo(), e);
            throw e;
        }
    }
}
