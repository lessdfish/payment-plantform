package com.payment.platform.gateway.dto;

import com.payment.platform.common.constant.ErrorCode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 风控检查结果。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RiskCheckResult {

    /** 是否通过风控检查 */
    private boolean passed;

    /** 拒绝原因（passed=false 时有值） */
    private String rejectReason;

    /** 拒绝错误码（passed=false 时有值） */
    private ErrorCode errorCode;
}
