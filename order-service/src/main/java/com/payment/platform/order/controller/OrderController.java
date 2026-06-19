package com.payment.platform.order.controller;

import com.payment.platform.common.result.ApiResult;
import com.payment.platform.order.entity.Order;
import com.payment.platform.order.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 订单 Controller。
 */
@RestController
@RequestMapping("/api/v1/order")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    @GetMapping("/{orderNo}")
    public ApiResult<Order> getByOrderNo(@PathVariable String orderNo) {
        return ApiResult.success(orderService.getByOrderNo(orderNo));
    }
}
