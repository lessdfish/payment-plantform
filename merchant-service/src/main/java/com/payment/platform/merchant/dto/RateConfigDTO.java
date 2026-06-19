package com.payment.platform.merchant.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 费率配置请求 DTO。
 */
@Data
@Schema(description = "费率配置请求")
public class RateConfigDTO {

    /** 渠道类型 */
    @NotBlank(message = "渠道类型不能为空")
    @Schema(description = "渠道类型", example = "WECHAT")
    private String channelType;

    /** 费率 */
    @NotNull(message = "费率不能为空")
    @DecimalMin(value = "0.0000", message = "费率不能为负数")
    @DecimalMax(value = "1.0000", message = "费率不能超过 100%")
    @Schema(description = "费率，如 0.0038 表示 0.38%", example = "0.0038")
    private BigDecimal feeRate;
}
