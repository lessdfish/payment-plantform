package com.payment.platform.gateway.client;

import com.payment.platform.common.result.ApiResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * 商户服务 REST 客户端。
 * <p>直连 merchant-service（localhost:8085），后续切换为 Nacos 服务发现。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MerchantClient {

    @Value("${services.merchant.base-url:http://localhost:8085}")
    private String baseUrl;

    private final RestClient client;
    private final Map<Long, CacheValue<String>> publicKeyCache = new ConcurrentHashMap<>();
    private final Map<String, CacheValue<BigDecimal>> feeRateCache = new ConcurrentHashMap<>();
    private static final long CACHE_TTL_MILLIS = 60_000;

    /**
     * 获取商户当前生效的 RSA 公钥。
     */
    public String getPublicKey(Long merchantId) {
        return getCached(publicKeyCache, merchantId, () -> {
            try {
            ApiResult<String> result = client.get()
                    .uri(baseUrl + "/api/v1/merchant/{merchantId}/key/public", merchantId)
                    .retrieve()
                    .body(new ParameterizedTypeReference<ApiResult<String>>() {});
            return result != null && result.getData() != null ? result.getData() : null;
            } catch (Exception e) {
                log.error("[MERCHANT-CLIENT] 获取公钥失败: merchantId={}", merchantId, e);
                return null;
            }
        });
    }

    /**
     * 获取商户在指定渠道的费率。
     */
    public BigDecimal getFeeRate(Long merchantId, String channelType) {
        String cacheKey = merchantId + ":" + channelType;
        return getCached(feeRateCache, cacheKey, () -> {
            try {
            ApiResult<BigDecimal> result = client.get()
                    .uri(baseUrl + "/api/v1/merchant/{merchantId}/rate/{channel}",
                            merchantId, channelType)
                    .retrieve()
                    .body(new ParameterizedTypeReference<ApiResult<BigDecimal>>() {});
            return result != null && result.getData() != null ? result.getData() : null;
            } catch (Exception e) {
                log.error("[MERCHANT-CLIENT] 获取费率失败: merchantId={}, channel={}",
                        merchantId, channelType, e);
                return null;
            }
        });
    }

    private <K, V> V getCached(Map<K, CacheValue<V>> cache, K key,
                               Supplier<V> loader) {
        long now = System.currentTimeMillis();
        CacheValue<V> cached = cache.get(key);
        if (cached != null && cached.expiresAt > now) {
            return cached.value;
        }
        V value = loader.get();
        if (value != null) {
            cache.put(key, new CacheValue<>(value, now + CACHE_TTL_MILLIS));
        }
        return value;
    }

    private record CacheValue<V>(V value, long expiresAt) {
    }
}
