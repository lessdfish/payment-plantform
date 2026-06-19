package com.payment.platform.common.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 退款请求参数。
 * <p>商户发起退款时传入，退款金额不能超过原订单金额。</p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "退款请求")
public class RefundRequest {

    /** 商户退款单号（幂等键） */
    @NotBlank(message = "退款单号不能为空")
    @Schema(description = "商户退款单号（幂等键）", example = "REF20240619001")
    private String outRefundNo;

    /** 原支付商户订单号 */
    @NotBlank(message = "原订单号不能为空")
    @Schema(description = "原支付商户订单号", example = "MCH20240619001")
    private String originOutTradeNo;

    /** 商户 ID */
    @NotNull(message = "商户 ID 不能为空")
    @Schema(description = "商户 ID", example = "10001")
    private Long merchantId;

    /** 退款金额（元，不能超过原支付金额） */
    @NotNull(message = "退款金额不能为空")
    @DecimalMin(value = "0.01", message = "退款金额最小 0.01 元")
    @Schema(description = "退款金额（元）", example = "50.00")
    private BigDecimal amount;

    /** 退款原因 */
    @Schema(description = "退款原因", example = "用户申请退款")
    private String reason;
}
