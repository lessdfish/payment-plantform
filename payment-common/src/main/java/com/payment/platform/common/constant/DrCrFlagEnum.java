package com.payment.platform.common.constant;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 借贷标识枚举。
 * <p>用于复式记账中标记每笔流水的借贷方向。</p>
 */
@Getter
@AllArgsConstructor
public enum DrCrFlagEnum {

    /** 借方（Debit）—— 资产的减少方，如商户账户扣款 */
    DEBIT("D", "借方"),

    /** 贷方（Credit）—— 资产的增加方，如平台账户收款 */
    CREDIT("C", "贷方");

    /** 借贷标识（DB 存储值，单字符） */
    private final String code;

    /** 借贷描述（中文） */
    private final String description;
}
