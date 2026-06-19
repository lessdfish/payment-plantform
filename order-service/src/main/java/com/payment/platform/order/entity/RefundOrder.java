package com.payment.platform.order.entity;

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
 * 退款单实体。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "refund_order")
public class RefundOrder {

    @Id
    private Long id;

    @Column(name = "refund_no", nullable = false, length = 32)
    private String refundNo;

    @Column(name = "out_refund_no", nullable = false, length = 64)
    private String outRefundNo;

    @Column(name = "origin_order_no", nullable = false, length = 32)
    private String originOrderNo;

    @Column(name = "merchant_id", nullable = false)
    private Long merchantId;

    @Column(name = "refund_amount", nullable = false, precision = 18, scale = 2)
    private BigDecimal refundAmount;

    @Column(nullable = false, length = 16)
    private String status;

    @Column(name = "create_time", updatable = false)
    private LocalDateTime createTime;

    @Column(name = "update_time")
    private LocalDateTime updateTime;
}
