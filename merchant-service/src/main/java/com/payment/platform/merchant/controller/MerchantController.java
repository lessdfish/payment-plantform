package com.payment.platform.merchant.controller;

import com.payment.platform.common.result.ApiResult;
import com.payment.platform.merchant.dto.MerchantRegisterDTO;
import com.payment.platform.merchant.entity.Merchant;
import com.payment.platform.merchant.service.MerchantService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 商户管理 Controller。
 * <p>提供商户入驻、查询、停用的 REST API。</p>
 */
@Tag(name = "商户管理", description = "商户入驻、查询、停用")
@RestController
@RequestMapping("/api/v1/merchant")
@RequiredArgsConstructor
public class MerchantController {

    private final MerchantService merchantService;

    /**
     * 商户入驻。
     * <p>注册后返回商户 ID、商户编号、API 密钥。</p>
     */
    @Operation(summary = "商户入驻")
    @PostMapping("/register")
    public ApiResult<Merchant> register(@Valid @RequestBody MerchantRegisterDTO dto) {
        Merchant merchant = merchantService.register(dto);
        return ApiResult.success(merchant);
    }

    /**
     * 查询商户信息。
     */
    @Operation(summary = "查询商户信息")
    @GetMapping("/{merchantId}")
    public ApiResult<Merchant> getById(@PathVariable Long merchantId) {
        Merchant merchant = merchantService.getById(merchantId);
        return ApiResult.success(merchant);
    }

    /**
     * 停用商户。
     * <p>停用后此商户的所有支付请求将被网关拒绝。</p>
     */
    @Operation(summary = "停用商户")
    @PutMapping("/{merchantId}/disable")
    public ApiResult<Void> disable(@PathVariable Long merchantId) {
        merchantService.disable(merchantId);
        return ApiResult.success();
    }
}
