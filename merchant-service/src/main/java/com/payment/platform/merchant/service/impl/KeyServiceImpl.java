package com.payment.platform.merchant.service.impl;

import com.payment.platform.common.util.RsaSignUtil;
import com.payment.platform.merchant.dto.KeyPairDTO;
import com.payment.platform.merchant.entity.MerchantKey;
import com.payment.platform.merchant.repository.MerchantKeyRepository;
import com.payment.platform.merchant.service.KeyService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.KeyPair;
import java.time.LocalDateTime;

/**
 * 密钥服务实现。
 * <p>RSA 2048 位密钥对：公钥存库供网关验签，私钥一次性返回给商户，平台不保存。</p>
 */
@Service
@RequiredArgsConstructor
public class KeyServiceImpl implements KeyService {

    private final MerchantKeyRepository merchantKeyRepository;

    /**
     * 生成 RSA 密钥对。
     * <p>流程：生成 2048 位 RSA → 公钥存库 → 私钥仅返回（不落库）。</p>
     */
    @Override
    @Transactional
    public KeyPairDTO generateKeyPair(Long merchantId) {
        // 生成 RSA 密钥对
        KeyPair keyPair = RsaSignUtil.generateKeyPair();
        String publicKey = RsaSignUtil.encodePublicKey(keyPair.getPublic());
        String privateKey = RsaSignUtil.encodePrivateKey(keyPair.getPrivate());

        // 公钥入库
        MerchantKey merchantKey = MerchantKey.builder()
                .merchantId(merchantId)
                .publicKey(publicKey)
                .keyType("RSA")
                .status("ACTIVE")
                .createTime(LocalDateTime.now())
                .build();
        merchantKeyRepository.save(merchantKey);

        // 返回密钥对（私钥仅此一次返回）
        return KeyPairDTO.builder()
                .merchantId(merchantId)
                .publicKey(publicKey)
                .privateKey(privateKey)
                .keyType("RSA")
                .build();
    }

    /**
     * 获取商户当前生效的 RSA 公钥。
     * <p>供支付网关验签时调用。</p>
     */
    @Override
    public String getActivePublicKey(Long merchantId) {
        return merchantKeyRepository.findByMerchantIdAndStatus(merchantId, "ACTIVE")
                .map(MerchantKey::getPublicKey)
                .orElse(null);
    }
}
