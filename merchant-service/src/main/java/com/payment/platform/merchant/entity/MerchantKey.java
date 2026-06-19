package com.payment.platform.merchant.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 商户 RSA 密钥表实体。
 * <p>存储商户的公钥，用于网关验签。私钥在生成时一次性返回给商户，平台不保存私钥。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "merchant_key")
public class MerchantKey {

    /** 主键，自增 */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 商户 ID，关联 merchant 表 */
    @Column(name = "merchant_id", nullable = false)
    private Long merchantId;

    /** RSA 公钥（Base64 编码），平台保存用于验签 */
    @Column(name = "public_key", nullable = false, columnDefinition = "TEXT")
    private String publicKey;

    /** 密钥类型，默认 RSA，未来可扩展国密 SM2 */
    @Column(name = "key_type", nullable = false, length = 16)
    private String keyType;

    /** 状态：ACTIVE=使用中 / INACTIVE=已废弃（密钥轮换时旧密钥标记此状态） */
    @Column(nullable = false, length = 16)
    private String status;

    /** 密钥创建时间 */
    @Column(name = "create_time", updatable = false)
    private LocalDateTime createTime;
}
