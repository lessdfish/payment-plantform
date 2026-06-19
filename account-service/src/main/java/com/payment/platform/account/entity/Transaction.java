package com.payment.platform.account.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 交易记录表实体。
 * <p>记录每一次账户变动的摘要。一笔 PAY 对应 1 条 transaction + 2 条 journalEntry（借+贷）。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "transaction")
public class Transaction {

    /** 主键，Snowflake */
    @Id
    private Long id;

    /** 交易流水号（全局唯一），关联 journal_entry 表 */
    @Column(name = "txn_id", nullable = false, length = 32)
    private String txnId;

    /** 商户 ID（分片键） */
    @Column(name = "merchant_id", nullable = false)
    private Long merchantId;

    /** 交易金额 */
    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal amount;

    /** 交易类型：PAY / REFUND / FREEZE / UNFREEZE / RECHARGE */
    @Column(name = "txn_type", nullable = false, length = 32)
    private String txnType;

    /** 外部订单号（幂等键），同一订单号不会重复处理 */
    @Column(name = "out_trade_no", length = 64)
    private String outTradeNo;

    /** 交易状态：PENDING / SUCCESS / FAILED */
    @Column(length = 16)
    private String status;

    /** 创建时间 */
    @Column(name = "create_time", updatable = false)
    private LocalDateTime createTime;
}
