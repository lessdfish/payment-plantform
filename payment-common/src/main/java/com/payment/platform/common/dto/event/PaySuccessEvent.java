package com.payment.platform.common.dto.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 支付成功事件（RocketMQ 事务消息体）。
 * <p>由 payment-gateway 发送，order-service 和 notification-service 消费。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaySuccessEvent implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 商户订单号 */
    private String outTradeNo;

    /** 商户 ID */
    private Long merchantId;

    /** 支付金额（元） */
    private BigDecimal amount;

    /** 渠道订单号 */
    private String channelOrderNo;

    /** 支付完成时间（时间戳毫秒） */
    private long paidTime;
}
