package com.payment.platform.merchant.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 费率配置表实体。
 * <p>定义每个商户在不同支付渠道的手续费率。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "rate_config")
public class RateConfig {

    /** 主键，自增 */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 商户 ID */
    @Column(name = "merchant_id", nullable = false)
    private Long merchantId;

    /** 渠道类型：WECHAT / ALIPAY / UNIONPAY */
    @Column(name = "channel_type", nullable = false, length = 32)
    private String channelType;

    /** 费率，如 0.0038 表示 0.38% */
    @Column(name = "fee_rate", nullable = false, precision = 5, scale = 4)
    private BigDecimal feeRate;

    /** 状态：ACTIVE=生效 / INACTIVE=失效 */
    @Column(nullable = false, length = 16)
    private String status;

    /** 创建时间 */
    @Column(name = "create_time", updatable = false)
    private LocalDateTime createTime;

    /** 更新时间 */
    @Column(name = "update_time")
    private LocalDateTime updateTime;
}
