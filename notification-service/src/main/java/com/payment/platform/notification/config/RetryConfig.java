package com.payment.platform.notification.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 回调重试退避策略：1min → 5min → 15min → 30min → 1h。
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "notification.retry")
public class RetryConfig {
    /** 退避延迟（毫秒），默认对应 1m/5m/15m/30m/1h */
    private long[] delaysMs = {60_000, 300_000, 900_000, 1_800_000, 3_600_000};
}
