package com.payment.platform.gateway.client;

import com.payment.platform.common.result.ApiResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;

/**
 * 商户服务 REST 客户端。
 * <p>直连 merchant-service（localhost:8085），后续切换为 Nacos 服务发现。</p>
 */
@Slf4j
@Component
public class MerchantClient {

    private static final String BASE_URL = "http://localhost:8085";

    private final RestClient client = RestClient.create();

    /**
     * 获取商户当前生效的 RSA 公钥。
     */
    public String getPublicKey(Long merchantId) {
        try {
            ApiResult<String> result = client.get()
                    .uri(BASE_URL + "/api/v1/merchant/{merchantId}/key/public", merchantId)
                    .retrieve()
                    .body(new ParameterizedTypeReference<ApiResult<String>>() {});
            return result != null && result.getData() != null ? result.getData() : null;
        } catch (Exception e) {
            log.error("[MERCHANT-CLIENT] 获取公钥失败: merchantId={}", merchantId, e);
            return null;
        }
    }

    /**
     * 获取商户在指定渠道的费率。
     */
    public BigDecimal getFeeRate(Long merchantId, String channelType) {
        try {
            ApiResult<BigDecimal> result = client.get()
                    .uri(BASE_URL + "/api/v1/merchant/{merchantId}/rate/{channel}",
                            merchantId, channelType)
                    .retrieve()
                    .body(new ParameterizedTypeReference<ApiResult<BigDecimal>>() {});
            return result != null && result.getData() != null ? result.getData() : null;
        } catch (Exception e) {
            log.error("[MERCHANT-CLIENT] 获取费率失败: merchantId={}, channel={}", merchantId, channelType, e);
            return null;
        }
    }
}
