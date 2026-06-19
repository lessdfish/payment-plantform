package com.payment.platform.common.exception;

import com.payment.platform.common.constant.ErrorCode;
import com.payment.platform.common.constant.PayResultEnum;
import lombok.Getter;

/**
 * 渠道异常。
 * <p>当支付渠道返回 FAIL 或 UNKNOWN 时抛出。
 * FAIL → 422，UNKNOWN → 202。</p>
 */
@Getter
public class ChannelException extends BusinessException {

    /** 渠道返回的原始结果 */
    private final PayResultEnum channelResult;

    public ChannelException(PayResultEnum channelResult, String detail) {
        super(
                channelResult == PayResultEnum.UNKNOWN
                        ? ErrorCode.CHANNEL_PROCESSING
                        : ErrorCode.CHANNEL_FAIL,
                detail
        );
        this.channelResult = channelResult;
    }
}
