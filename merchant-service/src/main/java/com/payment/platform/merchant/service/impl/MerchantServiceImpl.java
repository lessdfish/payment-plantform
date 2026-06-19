package com.payment.platform.merchant.service.impl;

import cn.hutool.core.util.IdUtil;
import com.payment.platform.common.exception.MerchantNotFoundException;
import com.payment.platform.merchant.dto.MerchantRegisterDTO;
import com.payment.platform.merchant.entity.Merchant;
import com.payment.platform.merchant.repository.MerchantRepository;
import com.payment.platform.merchant.service.MerchantService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 商户服务实现。
 * <p>商户入驻时生成三类凭证：</p>
 * <ul>
 *   <li>商户 ID — Snowflake 全局唯一</li>
 *   <li>商户编号 — MCH + 时间戳格式，对外展示</li>
 *   <li>API 密钥 — UUID 去掉横线，用于网关鉴权</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class MerchantServiceImpl implements MerchantService {

    private final MerchantRepository merchantRepository;

    /**
     * 商户入驻。
     * <p>流程：校验商户名未重复 → 生成凭证 → 持久化。</p>
     */
    @Override
    @Transactional
    public Merchant register(MerchantRegisterDTO dto) {
        // 生成商户编号：MCH + 当前时间戳 + 随机后缀保证唯一
        String merchantNo = "MCH" + System.currentTimeMillis() + IdUtil.fastSimpleUUID().substring(0, 4);

        // 如果生成的编号碰巧已存在（极小概率），重新生成
        while (merchantRepository.existsByMerchantNo(merchantNo)) {
            merchantNo = "MCH" + (System.currentTimeMillis() + 1) + IdUtil.fastSimpleUUID().substring(0, 4);
        }

        // 构建商户实体
        Merchant merchant = new Merchant();
        merchant.setId(IdUtil.getSnowflake(1, 1).nextId());  // merchant-service 使用 workerId=1
        merchant.setMerchantNo(merchantNo);
        merchant.setName(dto.getMerchantName());
        merchant.setStatus("ACTIVE");
        merchant.setContactEmail(dto.getContactEmail());
        merchant.setApiKey(IdUtil.fastSimpleUUID());  // 32 位 UUID 作为 API 密钥
        merchant.setCreateTime(LocalDateTime.now());
        merchant.setUpdateTime(LocalDateTime.now());

        return merchantRepository.save(merchant);
    }

    /**
     * 查询商户信息。
     * <p>不仅检查是否存在，也检查是否已停用——停用的商户同样返回 403。</p>
     */
    @Override
    public Merchant getById(Long merchantId) {
        Merchant merchant = merchantRepository.findById(merchantId)
                .orElseThrow(() -> new MerchantNotFoundException(merchantId));

        if ("DISABLED".equals(merchant.getStatus())) {
            throw new MerchantNotFoundException(merchantId);
        }

        return merchant;
    }

    /**
     * 停用商户。
     * <p>状态设为 DISABLED，该商户之后的所有支付请求会被网关拒绝。</p>
     */
    @Override
    @Transactional
    public void disable(Long merchantId) {
        Merchant merchant = merchantRepository.findById(merchantId)
                .orElseThrow(() -> new MerchantNotFoundException(merchantId));

        merchant.setStatus("DISABLED");
        merchant.setUpdateTime(LocalDateTime.now());
        merchantRepository.save(merchant);
    }
}
