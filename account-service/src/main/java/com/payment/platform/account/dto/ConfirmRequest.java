package com.payment.platform.account.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * TCC Confirm 请求 — 确认扣款。
 */
@Data
public class ConfirmRequest {

    /** TCC 事务 ID */
    @NotBlank
    private String tccId;
}
