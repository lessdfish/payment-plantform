package com.payment.platform.merchant.controller;

import com.payment.platform.common.result.ApiResult;
import com.payment.platform.merchant.dto.RateConfigDTO;
import com.payment.platform.merchant.entity.RateConfig;
import com.payment.platform.merchant.service.ConfigService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;

/**
 * 费率配置 Controller。
 * <p>提供渠道费率配置和查询接口。</p>
 */
@Tag(name = "费率配置", description = "支付渠道费率配置与查询")
@RestController
@RequestMapping("/api/v1/merchant")
@RequiredArgsConstructor
public class ConfigController {

    private final ConfigService configService;

    /**
     * 配置费率。
     */
    @Operation(summary = "配置渠道费率")
    @PostMapping("/{merchantId}/rate")
    public ApiResult<RateConfig> configureRate(
            @PathVariable Long merchantId,
            @Valid @RequestBody RateConfigDTO dto) {
        RateConfig config = configService.configureRate(merchantId, dto);
        return ApiResult.success(config);
    }

    /**
     * 查询商户指定渠道的费率（供支付网关内部调用）。
     */
    @Operation(summary = "查询渠道费率（内部接口）")
    @GetMapping("/{merchantId}/rate/{channel}")
    public ApiResult<BigDecimal> getFeeRate(
            @PathVariable Long merchantId,
            @PathVariable String channel) {
        BigDecimal feeRate = configService.getFeeRate(merchantId, channel);
        return ApiResult.success(feeRate);
    }
}
