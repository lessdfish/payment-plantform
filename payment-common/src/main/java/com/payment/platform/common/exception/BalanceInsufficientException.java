package com.payment.platform.common.exception;

import com.payment.platform.common.constant.ErrorCode;
import lombok.Getter;

import java.math.BigDecimal;

/**
 * 余额不足异常。
 * <p>商户可用余额不足以完成当前交易时抛出，返回 422。</p>
 */
@Getter
public class BalanceInsufficientException extends BusinessException {

    /** 当前可用余额 */
    private final BigDecimal availableBalance;

    /** 本次交易需要金额 */
    private final BigDecimal requiredAmount;

    public BalanceInsufficientException(BigDecimal availableBalance, BigDecimal requiredAmount) {
        super(ErrorCode.BALANCE_INSUFFICIENT,
                String.format("余额不足：可用 %s 元，需要 %s 元", availableBalance, requiredAmount));
        this.availableBalance = availableBalance;
        this.requiredAmount = requiredAmount;
    }
}
