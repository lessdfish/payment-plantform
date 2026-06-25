package com.payment.platform.notification.consumer;

import com.payment.platform.notification.service.NotifyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

/**
 * 消费商户回调延迟重试消息。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@RocketMQMessageListener(topic = "callback-retry",
        consumerGroup = "notify-callback-retry-consumer")
public class CallbackRetryConsumer implements RocketMQListener<Long> {

    private final NotifyService notifyService;

    @Override
    public void onMessage(Long recordId) {
        log.info("[NOTIFY-RETRY] 收到回调重试消息: recordId={}", recordId);
        notifyService.retry(recordId);
    }
}
