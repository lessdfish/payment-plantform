package com.payment.platform.merchant.service;

import com.payment.platform.merchant.dto.MerchantRegisterDTO;
import com.payment.platform.merchant.entity.Merchant;

/**
 * 商户服务接口 — 负责商户入驻、查询、停用。
 */
public interface MerchantService {

    /**
     * 商户入驻。
     * <p>生成全局唯一商户 ID（Snowflake）、商户编号、API 密钥，并持久化到数据库。</p>
     *
     * @param dto 入驻请求
     * @return 创建完成的商户实体
     */
    Merchant register(MerchantRegisterDTO dto);

    /**
     * 根据商户 ID 查询商户信息。
     * @param merchantId 商户 ID
     * @return 商户实体
     * @throws com.payment.platform.common.exception.MerchantNotFoundException 商户不存在或已停用
     */
    Merchant getById(Long merchantId);

    /**
     * 停用商户。
     * <p>将商户状态设为 DISABLED，停用后该商户的支付请求全部被拒。</p>
     *
     * @param merchantId 商户 ID
     */
    void disable(Long merchantId);
}
