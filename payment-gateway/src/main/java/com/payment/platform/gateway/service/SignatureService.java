package com.payment.platform.gateway.service;

import com.payment.platform.common.dto.request.PayRequest;

/**
 * 签名验证服务接口。
 * <p>提供 RSA 签名验证功能，确保商户请求未被篡改。</p>
 */
public interface SignatureService {

    /**
     * 验证商户请求的 RSA 签名。
     *
     * @param request   商户请求体
     * @param signature 商户传入的签名（Base64）
     * @param timestamp 请求时间戳
     * @param nonce     随机字符串（防重放）
     * @param merchantId 商户 ID
     * @return true=验签通过 / false=验签失败
     */
    boolean verify(PayRequest request, String signature, String timestamp,
                   String nonce, Long merchantId);
}
