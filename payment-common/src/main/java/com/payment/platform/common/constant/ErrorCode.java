package com.payment.platform.common.constant;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 统一错误码枚举。
 * <p>所有 API 响应均使用此枚举中的错误码，确保前后端、商户端错误信息一致。</p>
 */
@Getter
@AllArgsConstructor
public enum ErrorCode {

    /** 成功 */
    SUCCESS(0, "success"),

    /* ---------- 参数校验 400 ---------- */
    /** 请求参数校验失败 */
    PARAM_INVALID(40001, "请求参数校验失败"),

    /* ---------- 签名 & 鉴权 401 ---------- */
    /** 签名验证失败 */
    SIGN_INVALID(40101, "签名验证失败"),
    /** 时间戳超出有效窗口（5 分钟） */
    TIMESTAMP_EXPIRED(40102, "时间戳已过期"),
    /** nonce 重复，疑似重放攻击 */
    NONCE_REPLAY(40103, "请求已处理，请勿重复提交"),

    /* ---------- 商户 403 ---------- */
    /** 商户不存在或已停用 */
    MERCHANT_FORBIDDEN(40301, "商户不可用"),
    /** 商户未配置支付渠道 */
    CHANNEL_NOT_CONFIGURED(40302, "支付渠道未配置"),

    /* ---------- 幂等 ---------- */
    /** 幂等重复请求（非错误，正常返回原结果） */
    DUPLICATE(20001, "请求已处理，返回原结果"),

    /* ---------- 风控拦截 422 ---------- */
    /** 商户余额不足 */
    BALANCE_INSUFFICIENT(42201, "账户余额不足"),
    /** 超过单笔限额 */
    AMOUNT_EXCEED_SINGLE(42202, "超过单笔交易限额"),
    /** 超过日累计限额 */
    AMOUNT_EXCEED_DAILY(42203, "超过日累计交易限额"),
    /** 商户在黑名单中 */
    MERCHANT_BLACKLISTED(42204, "商户已被风控拦截"),

    /* ---------- 渠道异常 ---------- */
    /** 渠道明确返回失败 */
    CHANNEL_FAIL(42205, "支付渠道处理失败"),
    /** 渠道超时，处理中（202） */
    CHANNEL_PROCESSING(20201, "支付处理中，请稍后查询"),

    /* ---------- 系统错误 500 ---------- */
    /** 系统内部错误 */
    INTERNAL_ERROR(50001, "系统繁忙，请稍后重试"),
    /** 数据库操作失败 */
    DB_ERROR(50002, "数据操作异常"),
    /** 消息队列发送失败 */
    MQ_ERROR(50003, "消息发送异常"),

    /* ---------- 业务错误 ---------- */
    /** 订单不存在 */
    ORDER_NOT_FOUND(40401, "订单不存在"),
    /** 退款金额超过原订单金额 */
    REFUND_EXCEED_ORIGIN(42206, "退款金额不能超过原支付金额"),
    /** 订单状态不允许退款 */
    ORDER_NOT_REFUNDABLE(42207, "当前订单状态不允许退款");

    /** 业务状态码（HTTP 状态码 + 2 位序号，如 40101） */
    private final int code;

    /** 错误描述（中文） */
    private final String message;

    /**
     * 根据错误码查找枚举。
     * @param code 业务状态码
     * @return 对应的枚举值，未找到返回 INTERNAL_ERROR
     */
    public static ErrorCode fromCode(int code) {
        for (ErrorCode ec : values()) {
            if (ec.code == code) {
                return ec;
            }
        }
        return INTERNAL_ERROR;
    }
}
