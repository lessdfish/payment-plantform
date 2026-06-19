package com.payment.platform.common.dto.request;

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
