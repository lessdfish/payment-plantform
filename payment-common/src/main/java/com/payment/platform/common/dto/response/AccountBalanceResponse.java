package com.payment.platform.common.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 账户余额响应。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "账户余额响应")
public class AccountBalanceResponse {

    /** 账户 ID */
    @Schema(description = "账户 ID", example = "10001")
    private Long accountId;

    /** 账户总余额（元） */
    @Schema(description = "账户总余额（元）", example = "100000.00")
    private BigDecimal balance;

    /** 冻结金额（元） */
    @Schema(description = "冻结金额（元）", example = "500.00")
    private BigDecimal frozenAmount;

    /** 可用余额 = 总余额 - 冻结金额 */
    @Schema(description = "可用余额（元）", example = "99500.00")
    private BigDecimal availableBalance;
}
