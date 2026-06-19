package com.payment.platform.merchant.controller;

import com.payment.platform.common.result.ApiResult;
import com.payment.platform.merchant.dto.KeyPairDTO;
import com.payment.platform.merchant.service.KeyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 商户密钥管理 Controller。
 * <p>提供 RSA 密钥对生成和公钥查询接口。</p>
 */
@Tag(name = "密钥管理", description = "RSA 密钥对生成与公钥查询")
@RestController
@RequestMapping("/api/v1/merchant")
@RequiredArgsConstructor
public class KeyController {

    private final KeyService keyService;

    /**
     * 生成 RSA 密钥对。
     * <p>返回的公钥已存入平台，私钥仅此一次返回，商户必须自行保管好。</p>
     */
    @Operation(summary = "生成 RSA 密钥对",
            description = "公钥存入平台用于验签，私钥仅返回一次，商户必须妥善保管")
    @PostMapping("/{merchantId}/key/generate")
    public ApiResult<KeyPairDTO> generateKeyPair(@PathVariable Long merchantId) {
        KeyPairDTO keyPair = keyService.generateKeyPair(merchantId);
        return ApiResult.success(keyPair);
    }

    /**
     * 获取商户当前生效的公钥（供支付网关内部调用）。
     */
    @Operation(summary = "获取商户公钥（内部接口）")
    @GetMapping("/{merchantId}/key/public")
    public ApiResult<String> getPublicKey(@PathVariable Long merchantId) {
        String publicKey = keyService.getActivePublicKey(merchantId);
        return ApiResult.success(publicKey);
    }
}
