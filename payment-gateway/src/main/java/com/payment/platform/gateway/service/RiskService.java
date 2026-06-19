package com.payment.platform.gateway.service;

import com.payment.platform.common.dto.request.PayRequest;
import com.payment.platform.gateway.dto.RiskCheckResult;

/**
 * 风控服务接口 — 支付前风险检查。
 */
public interface RiskService {

    /**
     * 风控检查（含 IP 维度）。
     * @param request  支付请求
     * @param clientIp 客户端 IP
     * @return 风控检查结果
     */
    RiskCheckResult check(PayRequest request, String clientIp);
}
