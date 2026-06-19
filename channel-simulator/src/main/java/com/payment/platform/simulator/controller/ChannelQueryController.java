package com.payment.platform.simulator.controller;

import com.payment.platform.common.result.ApiResult;
import com.payment.platform.common.dto.response.ChannelQueryResponse;
import com.payment.platform.simulator.service.SimulatorService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 模拟查单 Controller。
 * <p>供支付网关在渠道返回 UNKNOWN 时轮询查单。</p>
 */
@Tag(name = "模拟查单", description = "渠道返回 UNKNOWN 后确认最终结果")
@RestController
@RequestMapping("/api/v1/simulator")
@RequiredArgsConstructor
public class ChannelQueryController {

    private final SimulatorService simulatorService;

    /**
     * 模拟查单。
     * <p>根据商户订单号查询支付最终状态。</p>
     */
    @Operation(summary = "模拟查单")
    @GetMapping("/query")
    public ApiResult<ChannelQueryResponse> query(
            @Parameter(description = "商户订单号", required = true)
            @RequestParam String outTradeNo) {
        ChannelQueryResponse response = simulatorService.query(outTradeNo);
        return ApiResult.success(response);
    }
}
