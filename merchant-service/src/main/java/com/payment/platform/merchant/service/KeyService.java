package com.payment.platform.merchant.service;

import com.payment.platform.merchant.dto.KeyPairDTO;

/**
 * 商户密钥服务接口 — 负责 RSA 密钥对的生成、公钥查询。
 */
public interface KeyService {

    /**
     * 为商户生成 RSA 密钥对。
     * <p>生成 2048 位 RSA 密钥对：公钥保存到数据库，私钥仅返回一次（平台不存储）。</p>
     *
     * @param merchantId 商户 ID
     * @return 密钥对 DTO（含公钥和私钥）
     */
    KeyPairDTO generateKeyPair(Long merchantId);

    /**
     * 获取商户当前生效的公钥（供支付网关验签用）。
     *
     * @param merchantId 商户 ID
     * @return 公钥字符串（Base64），如果未配置返回 null
     */
    String getActivePublicKey(Long merchantId);
}
