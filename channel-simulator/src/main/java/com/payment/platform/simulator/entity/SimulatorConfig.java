package com.payment.platform.simulator.entity;

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
 * 模拟器行为配置表实体。
 * <p>运行时动态控制模拟器的行为：延迟时长、成功率、UNKNOWN 率。
 * 修改后立即生效，无需重启服务。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "simulator_config")
public class SimulatorConfig {

    /** 主键，自增 */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    /** 渠道类型：DEFAULT=所有渠道 / WECHAT=仅微信 / ALIPAY=仅支付宝 */
    @Column(name = "channel_type", nullable = false, length = 32)
    private String channelType;

    /** 模拟延迟（毫秒），0=无延迟 */
    @Column(name = "delay_ms", nullable = false)
    private Integer delayMs;

    /** 成功率，0.00~1.00，如 0.80 表示 80% */
    @Column(name = "success_rate", nullable = false, precision = 3, scale = 2)
    private BigDecimal successRate;

    /** UNKNOWN 率，模拟超时不明确状态 */
    @Column(name = "unknown_rate", nullable = false, precision = 3, scale = 2)
    private BigDecimal unknownRate;

    /** 状态：ACTIVE=生效 */
    @Column(nullable = false, length = 16)
    private String status;

    /** 创建时间 */
    @Column(name = "create_time", updatable = false)
    private LocalDateTime createTime;

    /** 更新时间 */
    @Column(name = "update_time")
    private LocalDateTime updateTime;
}
