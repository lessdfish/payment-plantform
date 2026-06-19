package com.payment.platform.notification.config;

import lombok.Getter;

/**
 * 回调重试退避策略：1min → 5min → 15min → 30min → 1h。
 */
@Getter
public class RetryConfig {
    /** 退避延迟（毫秒），对应 1m/5m/15m/30m/1h */
    public static final long[] BACKOFF_DELAYS = {60_000, 300_000, 900_000, 1_800_000, 3_600_000};
    public static final int MAX_RETRIES = 5;
}
