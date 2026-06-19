package com.payment.platform.account.controller;

import com.payment.platform.account.service.AccountService;
import com.payment.platform.common.dto.response.AccountBalanceResponse;
import com.payment.platform.common.result.ApiResult;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;

/**
 * 账户 Controller — 余额查询（供网关调用）。
 */
@RestController
@RequestMapping("/api/v1/account")
@RequiredArgsConstructor
public class AccountController {

    private final AccountService accountService;

    /**
     * 查询商户账户余额。
     */
    @GetMapping("/balance/{merchantId}")
    public ApiResult<AccountBalanceResponse> getBalance(@PathVariable Long merchantId) {
        return ApiResult.success(accountService.getBalance(merchantId));
    }

    /**
     * 商户充值。
     */
    @PostMapping("/recharge/{merchantId}")
    public ApiResult<Void> recharge(@PathVariable Long merchantId,
                                     @RequestParam BigDecimal amount,
                                     @RequestParam String outTradeNo) {
        accountService.recharge(merchantId, amount, outTradeNo);
        return ApiResult.success();
    }
}
