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
 * 复式记账流水表实体（最核心的表）。
 * <p>每一笔交易产生两条流水，借-贷永远平衡。
 * 日终对账可自动校验：SELECT SUM(amount) WHERE txn_id=? AND dr_cr_flag='D'
 * 必须等于 SELECT SUM(amount) WHERE txn_id=? AND dr_cr_flag='C'。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "journal_entry")
public class JournalEntry {

    /** 主键，Snowflake */
    @Id
    private Long id;

    /** 交易流水号，同一笔交易的两条流水共享此号 */
    @Column(name = "txn_id", nullable = false, length = 32)
    private String txnId;

    /** 借方账户 ID（钱从哪个账户出） */
    @Column(name = "debit_account_id", nullable = false)
    private Long debitAccountId;

    /** 贷方账户 ID（钱进哪个账户） */
    @Column(name = "credit_account_id", nullable = false)
    private Long creditAccountId;

    /** 交易金额 */
    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal amount;

    /** 借贷标识：D=借方（资产减少），C=贷方（资产增加） */
    @Column(name = "dr_cr_flag", nullable = false, length = 1)
    private String drCrFlag;

    /** 交易类型：PAY / REFUND / FREEZE / UNFREEZE / RECHARGE */
    @Column(name = "txn_type", nullable = false, length = 32)
    private String txnType;

    /** 交易时间 */
    @Column(name = "txn_time", nullable = false)
    private LocalDateTime txnTime;

    /** 商户 ID（分片键） */
    @Column(name = "merchant_id", nullable = false)
    private Long merchantId;
}
