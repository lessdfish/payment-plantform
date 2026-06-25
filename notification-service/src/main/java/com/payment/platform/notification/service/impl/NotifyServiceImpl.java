package com.payment.platform.notification.service.impl;

import com.payment.platform.notification.config.RetryConfig;
import com.payment.platform.notification.entity.NotifyRecord;
import com.payment.platform.notification.repository.DirectNotifyStore;
import com.payment.platform.notification.service.NotifyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * 通知服务实现 — HTTP回调 + 退避重试。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotifyServiceImpl implements NotifyService {

    private final DirectNotifyStore notifyStore;
    private final RocketMQTemplate rocketMQTemplate;
    private final RetryConfig retryConfig;
    private final RestClient restClient = RestClient.create();

    @Override
    public void sendCallback(String outTradeNo, Long merchantId, String notifyUrl, String body) {
        NotifyRecord record = notifyStore.createIfAbsent(
                outTradeNo, merchantId, notifyUrl, body);
        if (record == null) {
            throw new IllegalStateException("通知记录创建失败: " + outTradeNo);
        }
        if (!"PENDING".equals(record.getStatus())) {
            if ("RETRYING".equals(record.getStatus())
                    && record.getNextRetryTime() != null) {
                long remainingDelay = Math.max(1, Duration.between(
                        LocalDateTime.now(), record.getNextRetryTime()).toMillis());
                rocketMQTemplate.syncSendDelayTimeMills(
                        "callback-retry", record.getId(), remainingDelay);
            }
            return;
        }
        doSend(record);
    }

    @Override
    public void retry(Long recordId) {
        NotifyRecord record = notifyStore.findById(recordId);
        if (record == null || "SUCCESS".equals(record.getStatus())
                || "FAILED".equals(record.getStatus())) {
            return;
        }
        if (record.getNextRetryTime() != null
                && record.getNextRetryTime().isAfter(LocalDateTime.now())) {
            return;
        }
        doSend(record);
    }

    private void doSend(NotifyRecord record) {
        try {
            restClient.post()
                    .uri(record.getNotifyUrl())
                    .body(record.getBody())
                    .retrieve()
                    .toBodilessEntity();
            notifyStore.markSuccess(record.getId());
            log.info("[NOTIFY] 回调成功: outTradeNo={}", record.getOutTradeNo());
        } catch (Exception e) {
            int retryCount = record.getRetryCount() + 1;
            long[] delays = retryConfig.getDelaysMs();
            if (retryCount <= delays.length) {
                long delay = delays[retryCount - 1];
                LocalDateTime nextRetryTime =
                        LocalDateTime.now().plus(Duration.ofMillis(delay));
                notifyStore.markRetrying(record.getId(), retryCount, nextRetryTime);
                rocketMQTemplate.syncSendDelayTimeMills("callback-retry",
                        record.getId(), delay);
                log.warn("[NOTIFY] 回调失败，将重试: outTradeNo={}, retry={}, delay={}ms",
                        record.getOutTradeNo(), retryCount, delay);
            } else {
                notifyStore.markFailed(record.getId(), retryCount);
                log.error("[NOTIFY] 回调失败，已达最大重试次数: outTradeNo={}", record.getOutTradeNo());
            }
        }
    }
}
