package com.payment.platform.reconciliation.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 对账差异表实体。
 * <p>记录内部流水与渠道账单比对不一致的条目。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "reconciliation_diff")
public class ReconciliationDiff {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "out_trade_no", nullable = false, length = 64)
    private String outTradeNo;

    @Column(name = "merchant_id", nullable = false)
    private Long merchantId;

    @Column(name = "internal_amount", precision = 18, scale = 2)
    private BigDecimal internalAmount;

    @Column(name = "channel_amount", precision = 18, scale = 2)
    private BigDecimal channelAmount;

    @Column(name = "diff_amount", precision = 18, scale = 2)
    private BigDecimal diffAmount;

    @Column(name = "diff_type", length = 32)
    private String diffType;

    @Column(length = 16)
    private String status;

    @Column(name = "create_time", updatable = false)
    private LocalDateTime createTime;
}
