package com.payment.platform.common.dto.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 退款成功事件（RocketMQ 事务消息体）。
 * <p>由 order-service 发送，notification-service 消费。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RefundSuccessEvent implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 商户退款单号 */
    private String outRefundNo;

    /** 原支付商户订单号 */
    private String originOutTradeNo;

    /** 商户 ID */
    private Long merchantId;

    /** 退款金额（元） */
    private BigDecimal refundAmount;

    /** 商户退款结果回调地址 */
    private String notifyUrl;
}
