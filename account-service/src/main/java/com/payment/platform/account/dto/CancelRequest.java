package com.payment.platform.account.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * TCC Cancel 请求 — 释放冻结。
 */
@Data
public class CancelRequest {

    /** TCC 事务 ID */
    @NotBlank
    private String tccId;
}
