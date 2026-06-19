package com.payment.platform.common.exception;

import com.payment.platform.common.constant.ErrorCode;
import lombok.Getter;

/**
 * 幂等重复请求异常。
 * <p>当相同的 outTradeNo 再次请求时抛出，返回 200 + DUPLICATE 错误码 + 原结果。
 * 这不是真正的错误，而是告诉商户"这个请求已经处理过了"。</p>
 */
@Getter
public class DuplicateRequestException extends BusinessException {

    /** 原请求的处理结果（JSON 字符串） */
    private final String originalResult;

    public DuplicateRequestException(String originalResult) {
        super(ErrorCode.DUPLICATE);
        this.originalResult = originalResult;
    }
}
