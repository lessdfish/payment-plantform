package com.payment.platform.common.constant;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 订单状态枚举。
 * <p>定义订单从创建到完结的完整生命周期状态。</p>
 */
@Getter
@AllArgsConstructor
public enum OrderStatusEnum {

    /** 订单已创建，等待支付 */
    CREATED("CREATED", "待支付"),

    /** 支付成功（渠道扣款成功 + 账户已扣款） */
    PAID("PAID", "已支付"),

    /** 已结算（订单确认完成，不可退款） */
    SETTLED("SETTLED", "已结算"),

    /** 退款处理中 */
    REFUNDING("REFUNDING", "退款中"),

    /** 已全额退款 */
    REFUNDED("REFUNDED", "已退款"),

    /** 超时关闭（30 分钟未支付自动关闭） */
    CLOSED("CLOSED", "已关闭");

    /** 状态编码（DB 存储值） */
    private final String code;

    /** 状态描述（中文） */
    private final String description;

    /**
     * 判断当前状态是否允许发起退款。
     * <p>只有已支付状态允许退款，退款中和已退款不可重复退款。</p>
     */
    public boolean isRefundable() {
        return this == PAID;
    }

    /**
     * 判断当前状态是否为终态（不可再变更）。
     */
    public boolean isTerminal() {
        return this == SETTLED || this == REFUNDED || this == CLOSED;
    }
}
