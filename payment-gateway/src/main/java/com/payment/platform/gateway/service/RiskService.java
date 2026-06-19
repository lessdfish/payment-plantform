package com.payment.platform.gateway.service;

import com.payment.platform.common.dto.request.PayRequest;
import com.payment.platform.gateway.dto.RiskCheckResult;

/**
 * 风控服务接口。
 * <p>在支付前对请求进行风险检查，拦截可疑交易。</p>
 */
public interface RiskService {

    /**
     * 风控检查。
     * <p>检查项：IP 频控、单笔限额、日累计限额、黑名单。</p>
     *
     * @param request 支付请求
     * @return 风控检查结果
     */
    RiskCheckResult check(PayRequest request);
}
