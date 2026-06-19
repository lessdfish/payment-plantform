package com.payment.platform.merchant.service.impl;

import com.payment.platform.merchant.dto.RateConfigDTO;
import com.payment.platform.merchant.entity.RateConfig;
import com.payment.platform.merchant.repository.RateConfigRepository;
import com.payment.platform.merchant.service.ConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 费率配置服务实现。
 * <p>商户可对不同的支付渠道（微信/支付宝/银联）配置不同的手续费率。</p>
 */
@Service
@RequiredArgsConstructor
public class ConfigServiceImpl implements ConfigService {

    private final RateConfigRepository rateConfigRepository;

    /**
     * 配置费率。
     * <p>始终新增一条记录，保持历史费率可追溯。旧费率不会被覆盖，只是状态区别于新费率。</p>
     */
    @Override
    @Transactional
    public RateConfig configureRate(Long merchantId, RateConfigDTO dto) {
        RateConfig config = RateConfig.builder()
                .merchantId(merchantId)
                .channelType(dto.getChannelType())
                .feeRate(dto.getFeeRate())
                .status("ACTIVE")
                .createTime(LocalDateTime.now())
                .updateTime(LocalDateTime.now())
                .build();
        return rateConfigRepository.save(config);
    }

    /**
     * 查询当前生效的费率。
     * <p>供支付网关计算手续费时调用。</p>
     */
    @Override
    public BigDecimal getFeeRate(Long merchantId, String channelType) {
        return rateConfigRepository
                .findByMerchantIdAndChannelTypeAndStatus(merchantId, channelType, "ACTIVE")
                .map(RateConfig::getFeeRate)
                .orElse(null);
    }
}
