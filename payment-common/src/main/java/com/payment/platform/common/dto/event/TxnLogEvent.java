package com.payment.platform.common.dto.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 交易流水事件（Kafka 消息体）。
 * <p>由 account-service 发送到 Kafka txn-log topic，
 * reconciliation-service 消费后进行实时对账。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TxnLogEvent implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 交易流水号（Snowflake） */
    private String txnId;

    /** 借方账户 ID */
    private Long debitAccountId;

    /** 贷方账户 ID */
    private Long creditAccountId;

    /** 交易金额（元） */
    private BigDecimal amount;

    /** 借贷标识：D=借 C=贷 */
    private String drCrFlag;

    /** 交易类型：PAY/REFUND/FREEZE/UNFREEZE */
    private String txnType;

    /** 交易时间（时间戳毫秒） */
    private long txnTime;

    /** 商户 ID（分片键） */
    private Long merchantId;

    /** 商户订单号（对账键） */
    private String outTradeNo;
}
