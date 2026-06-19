package com.payment.platform.common.dto.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 账单行 DTO。
 * <p>模拟 T-1 日对账单的每一行数据。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BillDTO {

    /** 渠道订单号 */
    private String channelOrderNo;

    /** 商户订单号 */
    private String outTradeNo;

    /** 交易金额 */
    private BigDecimal amount;

    /** 交易状态 */
    private String status;

    /** 交易时间 */
    private String txnTime;

    /** 渠道类型 */
    private String channelType;
}
