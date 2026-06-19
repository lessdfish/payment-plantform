package com.payment.platform.gateway.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 订单服务 REST 客户端（Phase 2 实现，当前为桩）。
 */
@Slf4j
@Component
public class OrderClient {

    /**
     * Phase 2 实现：创建订单。
     */
    public void createOrder(String outTradeNo, Long merchantId, java.math.BigDecimal amount) {
        log.info("[ORDER-STUB] 创建订单: outTradeNo={}, merchantId={}, amount={}", outTradeNo, merchantId, amount);
    }
}
