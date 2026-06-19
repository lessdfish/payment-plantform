package com.payment.platform.common.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 模拟查单响应 DTO。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChannelQueryResponse {

    /** 渠道订单号 */
    private String channelOrderNo;

    /** 商户订单号 */
    private String outTradeNo;

    /** 支付金额 */
    private BigDecimal amount;

    /** 最终状态：SUCCESS / FAIL / UNKNOWN */
    private String status;

    /** 查单完成时间 */
    private String queryTime;
}
