package com.payment.platform.account.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 账户表实体。
 * <p>每个商户对应一条账户记录。余额变更通过乐观锁（version 字段）保证并发安全。</p>
 * <p><b>关键规则：</b>可用余额 = balance - frozenAmount</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "account")
public class Account {

    /** 账户 ID，Snowflake 生成 */
    @Id
    private Long id;

    /** 商户 ID（唯一，分片键），一个商户只有一条账户记录 */
    @Column(name = "merchant_id", nullable = false, unique = true, updatable = false)
    private Long merchantId;

    /** 账户总余额（含冻结金额），如 10000.00 = 1 万元 */
    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal balance;

    /** 冻结金额（TCC Try 阶段预扣，暂不可用） */
    @Column(name = "frozen_amount", nullable = false, precision = 18, scale = 2)
    private BigDecimal frozenAmount;

    /** 乐观锁版本号，每次 UPDATE 必须 WHERE version = ? */
    @Version
    @Column(nullable = false)
    private Integer version;

    /** 创建时间 */
    @Column(name = "create_time", updatable = false)
    private LocalDateTime createTime;

    /** 更新时间 */
    @Column(name = "update_time")
    private LocalDateTime updateTime;

    /**
     * 计算可用余额。
     * @return balance - frozenAmount
     */
    public BigDecimal getAvailableBalance() {
        return balance.subtract(frozenAmount);
    }
}
