package com.payment.platform.common.constant;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 支付结果枚举。
 * <p>渠道返回的支付处理结果。只有 SUCCESS 和 FAIL 是明确终态，
 * UNKNOWN 表示需要进一步查单确认。</p>
 */
@Getter
@AllArgsConstructor
public enum PayResultEnum {

    /** 支付成功（明确终态） */
    SUCCESS("SUCCESS", "支付成功"),

    /** 支付失败（明确终态） */
    FAIL("FAIL", "支付失败"),

    /** 支付结果未知（如渠道超时，需查单确认） */
    UNKNOWN("UNKNOWN", "支付处理中");

    /** 结果编码（DB 存储值） */
    private final String code;

    /** 结果描述（中文） */
    private final String description;

    /**
     * 判断是否为明确的终态（成功或失败）。
     * <p>UNKNOWN 状态返回 false，需要后续查单确定。</p>
     */
    public boolean isFinal() {
        return this == SUCCESS || this == FAIL;
    }
}
