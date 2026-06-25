package com.payment.platform.gateway.service;

import com.payment.platform.common.dto.request.PayQueryRequest;
import com.payment.platform.common.dto.request.PayRequest;
import com.payment.platform.common.dto.response.PayQueryResponse;
import com.payment.platform.common.dto.response.PayResponse;

/**
 * 支付服务接口 — 支付下单和查询的核心逻辑。
 */
public interface PayService {

    /**
     * 支付下单。
     * <p>完整流程：幂等检查 → 验签 → 风控 → 渠道路由 → 调用渠道。
     * Phase 1 中暂不调用 TCC 账户扣款（Phase 2 补上）。</p>
     *
     * @param request   支付请求
     * @param signature RSA 签名
     * @param timestamp 请求时间戳
     * @param nonce     随机字符串
     * @param clientIp  客户端 IP
     * @return 支付响应
     */
    PayResponse createPay(PayRequest request, String signature,
                          String timestamp, String nonce, String clientIp);

    /**
     * 处理已可靠入队的支付请求。失败时抛出异常，由 MQ 重投。
     */
    void processAccepted(PayRequest request);

    /**
     * 支付查询。
     *
     * @param request 查询请求
     * @return 查询结果
     */
    PayQueryResponse queryPay(PayQueryRequest request);
}
