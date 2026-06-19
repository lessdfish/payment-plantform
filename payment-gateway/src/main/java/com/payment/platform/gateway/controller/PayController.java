package com.payment.platform.gateway.controller;

import com.payment.platform.common.dto.request.PayQueryRequest;
import com.payment.platform.common.dto.request.PayRequest;
import com.payment.platform.common.dto.response.PayQueryResponse;
import com.payment.platform.common.dto.response.PayResponse;
import com.payment.platform.common.result.ApiResult;
import com.payment.platform.gateway.service.PayService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 支付 Controller — 商户支付接口的入口。
 */
@Tag(name = "支付接口", description = "商户支付下单与查询")
@RestController
@RequestMapping("/api/v1/pay")
@RequiredArgsConstructor
public class PayController {

    private final PayService payService;

    /**
     * 支付下单。
     * <p>商户请求中的签名放在 HTTP Header 中：</p>
     * <ul>
     *   <li>X-Signature: RSA 签名（Base64）</li>
     *   <li>X-Timestamp: 请求时间戳（秒级 Unix 时间）</li>
     *   <li>X-Nonce: 随机字符串（32 位，防重放）</li>
     * </ul>
     */
    @Operation(summary = "支付下单",
            description = "商户发起支付请求，需在 Header 中携带 RSA 签名")
    @PostMapping("/create")
    public ApiResult<PayResponse> createPay(
            @Valid @RequestBody PayRequest request,
            @RequestHeader(value = "X-Signature", required = false) String signature,
            @RequestHeader(value = "X-Timestamp", required = false) String timestamp,
            @RequestHeader(value = "X-Nonce", required = false) String nonce) {
        PayResponse response = payService.createPay(request, signature, timestamp, nonce);
        return ApiResult.success(response);
    }

    /**
     * 支付查询。
     * <p>根据商户订单号查询支付状态。</p>
     */
    @Operation(summary = "支付查询")
    @GetMapping("/query")
    public ApiResult<PayQueryResponse> queryPay(
            @Valid @RequestBody PayQueryRequest request) {
        PayQueryResponse response = payService.queryPay(request);
        return ApiResult.success(response);
    }
}
