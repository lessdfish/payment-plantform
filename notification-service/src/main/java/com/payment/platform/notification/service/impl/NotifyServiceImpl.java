package com.payment.platform.notification.service.impl;

import com.payment.platform.notification.config.RetryConfig;
import com.payment.platform.notification.entity.NotifyRecord;
import com.payment.platform.notification.repository.NotifyRecordRepository;
import com.payment.platform.notification.service.NotifyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import java.time.LocalDateTime;

/**
 * 通知服务实现 — HTTP回调 + 退避重试。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotifyServiceImpl implements NotifyService {

    private final NotifyRecordRepository notifyRecordRepository;

    @Override
    public void sendCallback(String outTradeNo, Long merchantId, String notifyUrl, String body) {
        NotifyRecord record = NotifyRecord.builder()
                .merchantId(merchantId)
                .outTradeNo(outTradeNo)
                .notifyUrl(notifyUrl)
                .body(body)
                .status("PENDING")
                .retryCount(0)
                .createTime(LocalDateTime.now())
                .build();

        doSend(record);
    }

    private void doSend(NotifyRecord record) {
        try {
            RestClient.create().post()
                    .uri(record.getNotifyUrl())
                    .body(record.getBody())
                    .retrieve()
                    .toBodilessEntity();
            record.setStatus("SUCCESS");
            notifyRecordRepository.save(record);
            log.info("[NOTIFY] 回调成功: outTradeNo={}", record.getOutTradeNo());
        } catch (Exception e) {
            record.setRetryCount(record.getRetryCount() + 1);
            if (record.getRetryCount() < RetryConfig.MAX_RETRIES) {
                long delay = RetryConfig.BACKOFF_DELAYS[record.getRetryCount() - 1];
                record.setNextRetryTime(LocalDateTime.now().plusSeconds(delay / 1000));
                record.setStatus("RETRYING");
                log.warn("[NOTIFY] 回调失败，将重试: outTradeNo={}, retry={}, delay={}ms",
                        record.getOutTradeNo(), record.getRetryCount(), delay);
            } else {
                record.setStatus("FAILED");
                log.error("[NOTIFY] 回调失败，已达最大重试次数: outTradeNo={}", record.getOutTradeNo());
            }
            notifyRecordRepository.save(record);
        }
    }
}
