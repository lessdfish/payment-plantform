package com.payment.platform.gateway.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 渠道异步回调 Controller（Phase 3 实现，当前预留）。
 * <p>用于接收支付渠道的异步回调通知，当前渠道模拟器为同步调用，此接口暂时不用。</p>
 */
@Slf4j
@Tag(name = "渠道回调", description = "接收支付渠道异步通知（预留）")
@RestController
@RequestMapping("/api/v1/callback")
public class CallbackController {

    /**
     * 接收渠道回调（Phase 3 实现）。
     */
    @Operation(summary = "渠道异步回调通知（预留）")
    @PostMapping("/channel")
    public String channelCallback() {
        log.info("[CALLBACK] 收到渠道回调（暂未实现）");
        return "OK";
    }
}
