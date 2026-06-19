package com.payment.platform.common.exception;

import com.payment.platform.common.constant.ErrorCode;

/**
 * 商户不存在或已停用异常。
 * <p>当商户 ID 查询不到或状态为已停用时抛出，返回 403。</p>
 */
public class MerchantNotFoundException extends BusinessException {

    public MerchantNotFoundException(Long merchantId) {
        super(ErrorCode.MERCHANT_FORBIDDEN, "商户不存在或已停用: " + merchantId);
    }
}
