package com.payment.platform.common.constant;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 交易类型枚举。
 * <p>定义系统内所有账务变动的类型，用于流水分类和对账。</p>
 */
@Getter
@AllArgsConstructor
public enum TxnTypeEnum {

    /** 支付扣款（商户 → 平台） */
    PAY("PAY", "支付扣款"),

    /** 退款（平台 → 商户） */
    REFUND("REFUND", "退款"),

    /** 冻结金额（TCC Try 阶段） */
    FREEZE("FREEZE", "冻结"),

    /** 解冻金额（TCC Cancel 阶段） */
    UNFREEZE("UNFREEZE", "解冻"),

    /** 转账（商户 → 商户，Phase 5 实现） */
    TRANSFER("TRANSFER", "转账"),

    /** 充值（平台 → 商户） */
    RECHARGE("RECHARGE", "充值"),

    /** 提现（商户 → 外部卡，Phase 5 实现） */
    WITHDRAW("WITHDRAW", "提现");

    /** 交易类型编码（DB 存储值） */
    private final String code;

    /** 交易类型描述（中文） */
    private final String description;
}
