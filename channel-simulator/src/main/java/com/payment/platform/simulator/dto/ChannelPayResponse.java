package com.payment.platform.simulator.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 模拟支付响应 DTO。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "模拟支付响应")
public class ChannelPayResponse {

    /** 渠道订单号 */
    @Schema(description = "渠道订单号", example = "CH2024061900001")
    private String channelOrderNo;

    /** 商户订单号（原样返回） */
    @Schema(description = "商户订单号", example = "MCH20240619001")
    private String outTradeNo;

    /** 支付金额 */
    @Schema(description = "支付金额（元）", example = "99.99")
    private BigDecimal amount;

    /** 支付结果：SUCCESS / FAIL / UNKNOWN */
    @Schema(description = "支付结果", example = "SUCCESS")
    private String status;

    /** 结果描述 */
    @Schema(description = "结果描述", example = "支付成功")
    private String message;
}
