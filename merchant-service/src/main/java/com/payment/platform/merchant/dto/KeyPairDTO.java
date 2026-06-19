package com.payment.platform.merchant.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * RSA 密钥对响应 DTO。
 * <p>私钥仅在生成时返回一次，平台不保存私钥。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "RSA 密钥对")
public class KeyPairDTO {

    /** 商户 ID */
    @Schema(description = "商户 ID", example = "10001")
    private Long merchantId;

    /** RSA 公钥（Base64），平台保存用于验签 */
    @Schema(description = "RSA 公钥（Base64）", example = "MIIBIjANBgkqhki...")
    private String publicKey;

    /** RSA 私钥（Base64），一次性返回给商户，商户保存用于签名 */
    @Schema(description = "RSA 私钥（Base64），仅返回一次，商户妥善保存", example = "MIIEvQIBADANBgkqhki...")
    private String privateKey;

    /** 密钥类型 */
    @Schema(description = "密钥类型", example = "RSA")
    private String keyType;
}
