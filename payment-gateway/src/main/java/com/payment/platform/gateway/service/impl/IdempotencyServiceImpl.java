package com.payment.platform.gateway.service.impl;

import com.payment.platform.gateway.service.IdempotencyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * 幂等性校验服务实现。
 * <p>使用 Redis 存储已处理的订单号，TTL 72 小时。
 * 商户重复请求同一 outTradeNo 时直接返回原结果，避免重复扣款。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IdempotencyServiceImpl implements IdempotencyService {

    private final RedisTemplate<String, Object> redisTemplate;

    /** 幂等记录过期时间（72 小时） */
    private static final Duration TTL = Duration.ofHours(72);
    private static final Duration RESERVATION_TTL = TTL;

    /** Redis key 前缀 */
    private static final String KEY_PREFIX = "idem:";

    @Override
    public String reserve(Long merchantId, String outTradeNo, String processingJson) {
        String key = buildKey(merchantId, outTradeNo);
        Boolean acquired = redisTemplate.opsForValue()
                .setIfAbsent(key, processingJson, RESERVATION_TTL);
        if (Boolean.TRUE.equals(acquired)) {
            return null;
        }
        Object existing = redisTemplate.opsForValue().get(key);
        return existing == null ? processingJson : existing.toString();
    }

    /**
     * 检查订单号是否已处理。
     *
     * @return 已处理则返回原结果 JSON，未处理返回 null
     */
    @Override
    public String check(Long merchantId, String outTradeNo) {
        String key = buildKey(merchantId, outTradeNo);
        Object value = redisTemplate.opsForValue().get(key);
        if (value != null) {
            log.info("[IDEMPOTENT] 重复请求命中: merchantId={}, outTradeNo={}", merchantId, outTradeNo);
            return value.toString();
        }
        return null;
    }

    /**
     * 保存处理结果。
     */
    @Override
    public void save(Long merchantId, String outTradeNo, String resultJson) {
        String key = buildKey(merchantId, outTradeNo);
        redisTemplate.opsForValue().set(key, resultJson, TTL);
        log.info("[IDEMPOTENT] 保存幂等记录: merchantId={}, outTradeNo={}", merchantId, outTradeNo);
    }

    @Override
    public void release(Long merchantId, String outTradeNo) {
        redisTemplate.delete(buildKey(merchantId, outTradeNo));
    }

    private String buildKey(Long merchantId, String outTradeNo) {
        return KEY_PREFIX + merchantId + ":" + outTradeNo;
    }
}
