package com.payment.platform.notification.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "notify_record")
public class NotifyRecord {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "merchant_id", nullable = false) private Long merchantId;
    @Column(name = "out_trade_no", length = 64) private String outTradeNo;
    @Column(name = "notify_url", length = 512) private String notifyUrl;
    @Column(columnDefinition = "TEXT") private String body;
    @Column(length = 16) private String status;
    @Column(name = "retry_count") private Integer retryCount;
    @Column(name = "next_retry_time") private LocalDateTime nextRetryTime;
    @Column(name = "create_time", updatable = false) private LocalDateTime createTime;
}
