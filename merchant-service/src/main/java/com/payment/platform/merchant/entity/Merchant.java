package com.payment.platform.merchant.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 商户主表实体。
 * <p>存储入驻商户的基本信息，每个入驻商户对应一行记录。</p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "merchant")
public class Merchant {

    /** 商户 ID，Snowflake 生成 */
    @Id
    private Long id;

    /** 商户编号（唯一），对外展示用，格式 MCH + 时间戳 */
    @Column(name = "merchant_no", nullable = false, length = 32)
    private String merchantNo;

    /** 商户名称，如"小明科技" */
    @Column(nullable = false, length = 128)
    private String name;

    /** 状态：ACTIVE=正常 / DISABLED=已停用 */
    @Column(nullable = false, length = 16)
    private String status;

    /** 联系人邮箱，可为空 */
    @Column(name = "contact_email", length = 128)
    private String contactEmail;

    /** API 密钥（唯一），商户调用支付接口时的鉴权凭证 */
    @Column(name = "api_key", nullable = false, length = 64)
    private String apiKey;

    /** 创建时间 */
    @Column(name = "create_time", updatable = false)
    private LocalDateTime createTime;

    /** 最后更新时间 */
    @Column(name = "update_time")
    private LocalDateTime updateTime;
}
