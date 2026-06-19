package com.payment.platform.order.controller;

import com.payment.platform.common.result.ApiResult;
import com.payment.platform.order.entity.RefundOrder;
import com.payment.platform.order.service.RefundService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;

/**
 * 退款 Controller。
 */
@RestController
@RequestMapping("/api/v1/refund")
@RequiredArgsConstructor
public class RefundController {

    private final RefundService refundService;

    @PostMapping("/apply")
    public ApiResult<RefundOrder> apply(@RequestParam String outRefundNo,
                                         @RequestParam String originOrderNo,
                                         @RequestParam Long merchantId,
                                         @RequestParam BigDecimal refundAmount) {
        RefundOrder refund = refundService.apply(outRefundNo, originOrderNo,
                merchantId, refundAmount);
        return ApiResult.success(refund);
    }
}
