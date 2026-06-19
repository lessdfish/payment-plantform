package com.payment.platform.simulator.controller;

import com.payment.platform.common.result.ApiResult;
import com.payment.platform.simulator.dto.BillDTO;
import com.payment.platform.simulator.service.SimulatorService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 模拟账单 Controller。
 * <p>供对账服务拉取 T-1 日对账单。</p>
 */
@Tag(name = "模拟账单", description = "模拟渠道 T-1 日对账单下载")
@RestController
@RequestMapping("/api/v1/simulator")
@RequiredArgsConstructor
public class BillController {

    private final SimulatorService simulatorService;

    /**
     * 下载指定日期的模拟账单。
     *
     * @param date 账单日期，格式 yyyy-MM-dd
     * @return 账单行列表
     */
    @Operation(summary = "下载模拟账单")
    @GetMapping("/bill/{date}")
    public ApiResult<List<BillDTO>> getBill(
            @Parameter(description = "账单日期（yyyy-MM-dd）", required = true, example = "2024-06-19")
            @PathVariable String date) {
        List<BillDTO> bills = simulatorService.getBill(date);
        return ApiResult.success(bills);
    }
}
