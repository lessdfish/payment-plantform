package com.payment.platform.common.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 支付响应。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "支付响应")
public class PayResponse {

    /** 商户订单号（原样返回） */
    @Schema(description = "商户订单号", example = "MCH20240619001")
    private String outTradeNo;

    /** 支付状态：SUCCESS / FAIL / PROCESSING */
    @Schema(description = "支付状态", example = "SUCCESS")
    private String payStatus;

    /** 支付金额（元） */
    @Schema(description = "支付金额（元）", example = "99.99")
    private BigDecimal amount;

    /** 渠道订单号（模拟器生成的内部订单号） */
    @Schema(description = "渠道订单号", example = "CH2024061900001")
    private String channelOrderNo;

    /** 支付完成时间 */
    @Schema(description = "支付完成时间", example = "2024-06-19T12:00:00")
    private String paidTime;
}
