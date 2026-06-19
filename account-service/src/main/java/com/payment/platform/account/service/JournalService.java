package com.payment.platform.account.service;

import com.payment.platform.account.entity.JournalEntry;

import java.math.BigDecimal;

/**
 * 复式记账服务接口。
 */
public interface JournalService {

    /**
     * 生成一对借贷流水（借+贷，金额相等）。
     *
     * @param txnId          交易流水号
     * @param debitAccountId  借方账户（付款方）
     * @param creditAccountId 贷方账户（收款方）
     * @param amount          金额
     * @param txnType         交易类型
     * @param merchantId      商户 ID（分片键）
     */
    void record(String txnId, Long debitAccountId, Long creditAccountId,
                BigDecimal amount, String txnType, Long merchantId);
}
