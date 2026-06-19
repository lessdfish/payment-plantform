package com.payment.platform.order.service;

import com.payment.platform.order.entity.Order;

import java.math.BigDecimal;

/**
 * 订单服务接口。
 */
public interface OrderService {

    /** 根据内部订单号查询 */
    Order getByOrderNo(String orderNo);

    /** 更新订单状态 */
    void updateStatus(String orderNo, String newStatus);

    /** 创建订单 */
    Order create(String orderNo, String outTradeNo, Long merchantId,
                 BigDecimal amount, String channelOrderNo);
}
