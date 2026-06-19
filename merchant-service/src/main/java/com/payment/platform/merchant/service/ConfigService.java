package com.payment.platform.merchant.service;

import com.payment.platform.merchant.dto.RateConfigDTO;
import com.payment.platform.merchant.entity.RateConfig;

/**
 * 费率配置服务接口 — 负责商户支付渠道费率的 CRUD。
 */
public interface ConfigService {

    /**
     * 为商户配置指定渠道的费率。
     * <p>如果已存在该渠道的配置，会创建新配置（旧配置标记为 INACTIVE）。</p>
     *
     * @param merchantId 商户 ID
     * @param dto        费率配置请求
     * @return 新创建的费率配置实体
     */
    RateConfig configureRate(Long merchantId, RateConfigDTO dto);

    /**
     * 查询商户在指定渠道的当前费率。
     *
     * @param merchantId  商户 ID
     * @param channelType 渠道类型
     * @return 费率值，如未配置返回 null
     */
    java.math.BigDecimal getFeeRate(Long merchantId, String channelType);
}
