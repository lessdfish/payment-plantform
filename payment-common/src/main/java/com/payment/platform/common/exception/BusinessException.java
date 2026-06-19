package com.payment.platform.common.exception;

import com.payment.platform.common.constant.ErrorCode;
import lombok.Getter;

/**
 * 统一业务异常基类。
 * <p>所有业务异常继承此类，由 GlobalExceptionHandler 统一拦截处理。
 * 异常中包含错误码和 HTTP 状态码，确保返回给调用方的错误信息完整。</p>
 */
@Getter
public class BusinessException extends RuntimeException {

    /** 业务错误码 */
    private final int code;

    /** HTTP 状态码 */
    private final int httpStatus;

    /**
     * 使用预设错误码创建异常。
     * @param errorCode 错误码枚举
     */
    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.code = errorCode.getCode();
        this.httpStatus = extractHttpStatus(errorCode.getCode());
    }

    /**
     * 使用预设错误码 + 自定义消息创建异常。
     * @param errorCode 错误码枚举
     * @param detail    详细信息（覆盖默认 message）
     */
    public BusinessException(ErrorCode errorCode, String detail) {
        super(detail);
        this.code = errorCode.getCode();
        this.httpStatus = extractHttpStatus(errorCode.getCode());
    }

    /**
     * 从错误码中提取 HTTP 状态码。
     * <p>错误码格式为 HTTP 状态码 + 2 位序号，如 40101 → 401。</p>
     */
    private static int extractHttpStatus(int code) {
        return code / 100;
    }
}
