package com.payment.platform.gateway.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.payment.platform.common.dto.request.PayRequest;
import com.payment.platform.common.exception.SignatureException;
import com.payment.platform.common.util.RsaSignUtil;
import com.payment.platform.gateway.client.MerchantClient;
import com.payment.platform.gateway.service.SignatureService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * 签名验证服务实现。
 * <p>采用微信/支付宝同款的签名方案：</p>
 * <ol>
 *   <li>从 merchant-service 获取商户公钥</li>
 *   <li>构建签名串：method + url + timestamp + nonce + body</li>
 *   <li>用公钥验签</li>
 *   <li>校验时间戳是否在 5 分钟有效窗口内</li>
 *   <li>校验 nonce 是否重复（防重放）</li>
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SignatureServiceImpl implements SignatureService {

    /** 时间戳有效窗口（秒） */
    private static final long TIMESTAMP_WINDOW_SECONDS = 300; // 5 分钟

    private final MerchantClient merchantClient;
    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;

    @Override
    public boolean verify(PayRequest request, String signature, String timestamp,
                          String nonce, Long merchantId) {
        // 1. 获取商户公钥
        String publicKey = merchantClient.getPublicKey(merchantId);
        if (publicKey == null) {
            log.warn("[SIGN] 商户 {} 未配置公钥", merchantId);
            throw new SignatureException("商户未配置签名密钥");
        }

        // 2. 校验时间戳窗口
        long currentTime = System.currentTimeMillis() / 1000; // 秒级
        long requestTime;
        try {
            requestTime = Long.parseLong(timestamp);
        } catch (NumberFormatException e) {
            log.warn("[SIGN] 时间戳格式错误: {}", timestamp);
            throw new SignatureException("时间戳格式错误");
        }

        if (Math.abs(currentTime - requestTime) > TIMESTAMP_WINDOW_SECONDS) {
            log.warn("[SIGN] 时间戳过期: current={}, request={}, diff={}s",
                    currentTime, requestTime, Math.abs(currentTime - requestTime));
            throw new SignatureException("请求已过期，请重新生成签名");
        }

        // 3. 校验 nonce 防重放
        String nonceKey = "nonce:" + merchantId + ":" + nonce;
        Boolean nonceExists = redisTemplate.opsForValue()
                .setIfAbsent(nonceKey, "1", Duration.ofSeconds(TIMESTAMP_WINDOW_SECONDS));
        if (Boolean.FALSE.equals(nonceExists)) {
            log.warn("[SIGN] nonce 重复，疑似重放攻击: merchantId={}, nonce={}", merchantId, nonce);
            throw new SignatureException("nonce 重复，疑似重放攻击");
        }

        // 4. 构建签名串并验签
        try {
            String body = objectMapper.writeValueAsString(request);
            String signContent = "POST" + "\n"
                    + "/api/v1/pay/create" + "\n"
                    + timestamp + "\n"
                    + nonce + "\n"
                    + body + "\n";

            boolean valid = RsaSignUtil.verify(signContent, signature, publicKey);
            if (valid) {
                log.info("[SIGN] 验签通过: merchantId={}, outTradeNo={}", merchantId, request.getOutTradeNo());
            } else {
                log.warn("[SIGN] 验签失败: merchantId={}, outTradeNo={}", merchantId, request.getOutTradeNo());
                throw new SignatureException("签名验证失败");
            }
            return true;
        } catch (SignatureException e) {
            throw e;
        } catch (Exception e) {
            log.error("[SIGN] 验签过程异常", e);
            throw new SignatureException("签名验证过程异常");
        }
    }
}
