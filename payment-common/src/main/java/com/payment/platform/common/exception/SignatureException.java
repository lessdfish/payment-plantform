package com.payment.platform.common.exception;

import com.payment.platform.common.constant.ErrorCode;

/**
 * 签名验证异常。
 * <p>当商户请求的 RSA 签名验证不通过时抛出，返回 401。</p>
 */
public class SignatureException extends BusinessException {

    public SignatureException() {
        super(ErrorCode.SIGN_INVALID);
    }

    public SignatureException(String detail) {
        super(ErrorCode.SIGN_INVALID, detail);
    }
}
