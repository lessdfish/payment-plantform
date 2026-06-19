package com.payment.platform.simulator.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 模拟渠道订单表实体。
 * <p>模拟器收到支付请求后在此表创建记录，模拟真实支付渠道（微信/支付宝）的订单行为。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "channel_order")
public class ChannelOrder {

    /** 主键，Snowflake */
    @Id
    private Long id;

    /** 渠道订单号（唯一），如 WX202406190001 */
    @Column(name = "channel_order_no", nullable = false, length = 64)
    private String channelOrderNo;

    /** 商户订单号，对应商户请求中的 outTradeNo */
    @Column(name = "out_trade_no", nullable = false, length = 64)
    private String outTradeNo;

    /** 交易金额（元） */
    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal amount;

    /** 支付状态：SUCCESS / FAIL / UNKNOWN */
    @Column(nullable = false, length = 16)
    private String status;

    /** 渠道类型：MOCK（默认）/ WECHAT / ALIPAY / UNIONPAY */
    @Column(name = "channel_type", length = 32)
    private String channelType;

    /** 订单创建时间 */
    @Column(name = "create_time", updatable = false)
    private LocalDateTime createTime;
}
