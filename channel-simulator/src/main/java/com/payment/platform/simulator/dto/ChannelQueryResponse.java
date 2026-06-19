package com.payment.platform.simulator.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 模拟查单响应 DTO。
 * <p>当渠道返回 UNKNOWN 时，网关轮询查单接口确认最终结果。</p>
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

    /** 最终状态：SUCCESS / FAIL / UNKNOWN（如果仍然不确定） */
    private String status;

    /** 查单完成时间 */
    private String queryTime;
}
