package com.payment.platform.gateway.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 渠道路由结果。
 * <p>根据商户配置和金额选择最优支付渠道后返回。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RouteResult {

    /** 渠道类型：WECHAT / ALIPAY / UNIONPAY */
    private String channelType;

    /** 渠道服务地址（通过 Nacos 发现） */
    private String channelUrl;

    /** 手续费率 */
    private BigDecimal feeRate;
}
