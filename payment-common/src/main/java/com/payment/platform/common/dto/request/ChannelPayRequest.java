package com.payment.platform.common.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 模拟支付请求 DTO（内部接口，由网关调用渠道模拟器）。
 */
@Data
@Schema(description = "模拟支付请求")
public class ChannelPayRequest {

    @NotBlank
    @Schema(description = "商户订单号", example = "MCH20240619001")
    private String outTradeNo;

    @NotNull
    @DecimalMin("0.01")
    @Schema(description = "支付金额（元）", example = "99.99")
    private BigDecimal amount;

    @NotBlank
    @Schema(description = "渠道类型", example = "WECHAT")
    private String channelType;
}
