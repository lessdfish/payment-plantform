package com.payment.platform.common.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

/**
 * TCC Try 请求 — 冻结商户余额。
 */
@Data
public class TryRequest {

    /** 商户 ID */
    @NotNull
    private Long merchantId;

    /** 冻结金额 */
    @NotNull
    @DecimalMin("0.01")
    private BigDecimal amount;

    /** 业务单号（幂等键） */
    @NotBlank
    private String bizOrderNo;
}
