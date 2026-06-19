package com.payment.platform.simulator.controller;

import com.payment.platform.common.result.ApiResult;
import com.payment.platform.common.dto.request.ChannelPayRequest;
import com.payment.platform.common.dto.response.ChannelPayResponse;
import com.payment.platform.simulator.service.SimulatorService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 模拟支付 Controller。
 * <p>供支付网关调用，模拟真实支付渠道的支付接口。</p>
 */
@Tag(name = "模拟支付", description = "模拟微信/支付宝/银联的支付接口")
@RestController
@RequestMapping("/api/v1/simulator")
@RequiredArgsConstructor
public class ChannelPayController {

    private final SimulatorService simulatorService;

    /**
     * 模拟发起支付。
     * <p>根据配置概率随机返回 SUCCESS / FAIL / UNKNOWN。</p>
     */
    @Operation(summary = "模拟发起支付")
    @PostMapping("/pay")
    public ApiResult<ChannelPayResponse> pay(@Valid @RequestBody ChannelPayRequest request) {
        ChannelPayResponse response = simulatorService.pay(request);
        return ApiResult.success(response);
    }
}
