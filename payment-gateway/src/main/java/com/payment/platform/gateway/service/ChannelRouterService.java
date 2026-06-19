package com.payment.platform.gateway.service;

import com.payment.platform.gateway.dto.RouteResult;

import java.math.BigDecimal;

/**
 * 渠道路由服务接口。
 * <p>根据商户的渠道配置和当前费率，选择最优支付渠道。</p>
 */
public interface ChannelRouterService {

    /**
     * 选择最优支付渠道。
     * <p>当前简化实现：按商户费率从低到高选择第一个可用渠道。
     * 生产环境可扩展为多维度路由（渠道可用性、费率、成功率、延迟等）。</p>
     *
     * @param merchantId 商户 ID
     * @param amount     支付金额
     * @return 路由结果（渠道类型 + 费率）
     */
    RouteResult route(Long merchantId, BigDecimal amount);
}
