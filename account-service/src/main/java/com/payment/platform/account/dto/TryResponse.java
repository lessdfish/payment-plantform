package com.payment.platform.account.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * TCC Try 响应。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TryResponse {

    /** TCC 事务 ID，后续 Confirm/Cancel 使用 */
    private String tccId;

    /** 冻结金额 */
    private BigDecimal frozenAmount;
}
