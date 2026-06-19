package com.payment.platform.common.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 支付查询响应。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "支付查询响应")
public class PayQueryResponse {

    /** 商户订单号 */
    @Schema(description = "商户订单号", example = "MCH20240619001")
    private String outTradeNo;

    /** 支付状态 */
    @Schema(description = "支付状态", example = "SUCCESS")
    private String payStatus;

    /** 支付金额（元） */
    @Schema(description = "支付金额（元）", example = "99.99")
    private BigDecimal amount;
}
