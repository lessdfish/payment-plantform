package com.payment.platform.order.service;

import com.payment.platform.order.entity.RefundOrder;

import java.math.BigDecimal;

/**
 * 退款服务接口。
 */
public interface RefundService {

    /** 申请退款 */
    RefundOrder apply(String outRefundNo, String originOrderNo, Long merchantId,
                       BigDecimal refundAmount);
}
