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
 * 支付下单请求参数。
 * <p>商户调用支付网关时传入，需附带 RSA 签名用于验签。</p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "支付下单请求")
public class PayRequest {

    /** 商户订单号（幂等键，商户侧唯一） */
    @NotBlank(message = "商户订单号不能为空")
    @Schema(description = "商户订单号（幂等键）", example = "MCH20240619001")
    private String outTradeNo;

    /** 商户 ID（支付网关根据此 ID 验签和路由） */
    @NotNull(message = "商户 ID 不能为空")
    @Schema(description = "商户 ID", example = "10001")
    private Long merchantId;

    /** 支付金额（元，支持两位小数） */
    @NotNull(message = "支付金额不能为空")
    @DecimalMin(value = "0.01", message = "支付金额最小 0.01 元")
    @Schema(description = "支付金额（元）", example = "99.99")
    private BigDecimal amount;

    /** 货币类型（默认 CNY） */
    @Schema(description = "货币类型", example = "CNY")
    private String currency = "CNY";

    /** 商户回调地址（支付成功后通知） */
    @NotBlank(message = "回调地址不能为空")
    @Schema(description = "支付结果回调 URL", example = "https://merchant.com/callback")
    private String notifyUrl;

    /** 商品/服务描述 */
    @Schema(description = "商品/服务描述", example = "会员充值")
    private String subject;
}
