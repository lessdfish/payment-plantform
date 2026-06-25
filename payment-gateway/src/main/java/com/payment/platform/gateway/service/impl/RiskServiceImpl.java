package com.payment.platform.gateway.service.impl;

import com.payment.platform.common.dto.request.PayRequest;
import com.payment.platform.common.constant.ErrorCode;
import com.payment.platform.gateway.dto.RiskCheckResult;
import com.payment.platform.gateway.service.RiskService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.concurrent.TimeUnit;

/**
 * 风控服务实现 — 四道防线。
 * <p>Phase 4 扩展：IP 频控 + 日累计限额 + 黑名单 + 单笔限额。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RiskServiceImpl implements RiskService {

    private static final BigDecimal MAX_SINGLE_AMOUNT = new BigDecimal("50000.00");
    private static final BigDecimal MAX_DAILY_AMOUNT = new BigDecimal("1000000.00");  // 日累计 100 万
    private static final int IP_MAX_REQUESTS = 50;      // 单 IP 在窗口内最多 50 次
    private static final int IP_WINDOW_SECONDS = 300;

    private final RedisTemplate<String, Object> redisTemplate;

    @org.springframework.beans.factory.annotation.Value("${pressure.enabled:false}")
    private boolean pressureEnabled;

    @Override
    public RiskCheckResult check(PayRequest request, String clientIp) {
        Long merchantId = request.getMerchantId();

        // 1. 黑名单检查
        Boolean blacklisted = redisTemplate.opsForSet().isMember("blacklist:merchant", merchantId.toString());
        if (Boolean.TRUE.equals(blacklisted)) {
            log.warn("[RISK] 商户在黑名单中: merchantId={}", merchantId);
            return RiskCheckResult.builder()
                    .passed(false)
                    .errorCode(ErrorCode.MERCHANT_BLACKLISTED)
                    .rejectReason("商户已被风控拦截")
                    .build();
        }

        if (!pressureEnabled) {
            // 2. IP 频控（滑动窗口 + Redis）
            String ipKey = "rate:ip:" + clientIp;
            Long ipCount = redisTemplate.opsForValue().increment(ipKey);
            if (ipCount != null && ipCount == 1) {
                redisTemplate.expire(ipKey, IP_WINDOW_SECONDS, TimeUnit.SECONDS);
            }
            if (ipCount != null && ipCount > IP_MAX_REQUESTS) {
                log.warn("[RISK] IP 频控拦截: ip={}, count={}", clientIp, ipCount);
                return RiskCheckResult.builder()
                        .passed(false)
                        .errorCode(ErrorCode.RATE_LIMITED)
                        .rejectReason("请求过于频繁，请稍后重试")
                        .build();
            }
        }

        // 3. 单笔限额
        if (request.getAmount().compareTo(MAX_SINGLE_AMOUNT) > 0) {
            return RiskCheckResult.builder().passed(false)
                    .errorCode(ErrorCode.AMOUNT_EXCEED_SINGLE)
                    .rejectReason("单笔交易金额超过上限 " + MAX_SINGLE_AMOUNT + " 元").build();
        }

        // 4. 日累计限额
        if (!pressureEnabled) {
            String dailyKey = "daily:amount:" + merchantId + ":" + LocalDate.now();
            Long dailyTotal = redisTemplate.opsForValue().increment(dailyKey,
                    request.getAmount().longValue());
            if (dailyTotal != null && dailyTotal == request.getAmount().longValue()) {
                redisTemplate.expire(dailyKey, Duration.ofDays(1));
            }
            if (dailyTotal != null && dailyTotal > MAX_DAILY_AMOUNT.longValue()) {
                log.warn("[RISK] 日累计超限: merchantId={}, dailyTotal={}", merchantId, dailyTotal);
                return RiskCheckResult.builder().passed(false)
                        .errorCode(ErrorCode.AMOUNT_EXCEED_DAILY)
                        .rejectReason("超过日累计交易限额").build();
            }
        }

        if (request.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            return RiskCheckResult.builder()
                    .passed(false)
                    .errorCode(ErrorCode.PARAM_INVALID)
                    .rejectReason("交易金额必须大于 0")
                    .build();
        }

        return RiskCheckResult.builder().passed(true).build();
    }
}
