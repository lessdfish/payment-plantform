package com.payment.platform.account.service;

import com.payment.platform.common.dto.response.AccountBalanceResponse;

import java.math.BigDecimal;

/**
 * 账户服务接口 — 余额查询、充值。
 */
public interface AccountService {

    /**
     * 查询商户账户余额（含冻结金额和可用余额）。
     */
    AccountBalanceResponse getBalance(Long merchantId);

    /**
     * 商户充值（平台→商户，单边操作不涉及 TCC）。
     */
    void recharge(Long merchantId, BigDecimal amount, String outTradeNo);
}
