package com.payment.platform.common.constant;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 支付渠道枚举。
 * <p>定义系统支持的支付渠道类型，渠道模拟器据此提供不同的模拟行为。</p>
 */
@Getter
@AllArgsConstructor
public enum ChannelEnum {

    /** 微信支付 */
    WECHAT("WECHAT", "微信支付"),

    /** 支付宝 */
    ALIPAY("ALIPAY", "支付宝"),

    /** 银联支付 */
    UNIONPAY("UNIONPAY", "银联支付");

    /** 渠道编码（DB 存储值） */
    private final String code;

    /** 渠道名称（中文） */
    private final String name;
}
