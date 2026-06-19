package com.payment.platform.merchant.repository;

import com.payment.platform.merchant.entity.RateConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 费率配置数据访问层。
 */
@Repository
public interface RateConfigRepository extends JpaRepository<RateConfig, Long> {

    /**
     * 查询商户所有生效的费率配置。
     * @param merchantId 商户 ID
     * @param status     状态（ACTIVE）
     * @return 费率配置列表
     */
    List<RateConfig> findByMerchantIdAndStatus(Long merchantId, String status);

    /**
     * 查询商户指定渠道的费率。
     * @param merchantId  商户 ID
     * @param channelType 渠道类型
     * @param status      状态
     * @return 费率配置（可能为空）
     */
    Optional<RateConfig> findByMerchantIdAndChannelTypeAndStatus(
            Long merchantId, String channelType, String status);
}
