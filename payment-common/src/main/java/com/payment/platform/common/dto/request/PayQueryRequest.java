package com.payment.platform.common.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 支付查询请求参数。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "支付查询请求")
public class PayQueryRequest {

    /** 商户订单号 */
    @NotBlank(message = "商户订单号不能为空")
    @Schema(description = "商户订单号", example = "MCH20240619001")
    private String outTradeNo;

    /** 商户 ID */
    @NotNull(message = "商户 ID 不能为空")
    @Schema(description = "商户 ID", example = "10001")
    private Long merchantId;
}
