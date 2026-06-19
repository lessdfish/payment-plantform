package com.payment.platform.common.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 退款响应。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "退款响应")
public class RefundResponse {

    /** 商户退款单号（原样返回） */
    @Schema(description = "商户退款单号", example = "REF20240619001")
    private String outRefundNo;

    /** 退款状态：REFUNDING / REFUNDED / FAILED */
    @Schema(description = "退款状态", example = "REFUNDED")
    private String refundStatus;

    /** 退款金额（元） */
    @Schema(description = "退款金额（元）", example = "50.00")
    private BigDecimal refundAmount;
}
