package com.payment.platform.gateway.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.payment.platform.common.dto.request.PayQueryRequest;
import com.payment.platform.common.dto.request.PayRequest;
import com.payment.platform.common.dto.response.PayQueryResponse;
import com.payment.platform.common.dto.response.PayResponse;
import com.payment.platform.common.exception.BalanceInsufficientException;
import com.payment.platform.common.exception.ChannelException;
import com.payment.platform.common.exception.DuplicateRequestException;
import com.payment.platform.common.exception.SignatureException;
import com.payment.platform.gateway.client.AccountClient;
import com.payment.platform.gateway.client.ChannelSimulatorClient;
import com.payment.platform.gateway.client.OrderClient;
import com.payment.platform.gateway.dto.RiskCheckResult;
import com.payment.platform.gateway.dto.RouteResult;
import com.payment.platform.gateway.service.ChannelRouterService;
import com.payment.platform.gateway.service.IdempotencyService;
import com.payment.platform.gateway.service.PayService;
import com.payment.platform.gateway.service.RiskService;
import com.payment.platform.gateway.service.SignatureService;
import com.payment.platform.common.dto.request.ChannelPayRequest;
import com.payment.platform.common.dto.response.ChannelPayResponse;
import com.payment.platform.common.dto.response.ChannelQueryResponse;
import com.payment.platform.common.dto.response.TryResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 支付服务实现 — 支付下单的核心业务逻辑。
 *
 * <p>完整支付链路（Phase 1 实现步骤 1-5，Phase 2 补充步骤 6-10）：</p>
 * <ol>
 *   <li>幂等检查 → 命中则直接返回原结果</li>
 *   <li>验签 → RSA 签名验证</li>
 *   <li>风控检查 → 单笔限额等</li>
 *   <li>渠道路由 → 选择最优渠道</li>
 *   <li>调用渠道模拟器 → SUCCESS/FAIL/UNKNOWN</li>
 *   <li>[Phase 2] TCC Try → 冻结商户余额</li>
 *   <li>[Phase 2] TCC Confirm/Cancel → 确认或取消扣款</li>
 *   <li>[Phase 2] 发送事务消息 → RocketMQ pay-success</li>
 *   <li>记录幂等结果</li>
 *   <li>返回支付结果</li>
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PayServiceImpl implements PayService {

    private final IdempotencyService idempotencyService;
    private final SignatureService signatureService;
    private final RiskService riskService;
    private final ChannelRouterService channelRouterService;
    private final ChannelSimulatorClient channelSimulatorClient;
    private final AccountClient accountClient;
    private final OrderClient orderClient;
    private final ObjectMapper objectMapper;

    /**
     * 支付下单。
     * <p>同步返回支付结果。渠道返回 UNKNOWN 时异步轮询查单。</p>
     */
    @Override
    public PayResponse createPay(PayRequest request, String signature,
                                  String timestamp, String nonce, String clientIp) {
        Long merchantId = request.getMerchantId();
        String outTradeNo = request.getOutTradeNo();

        // 1. 幂等检查
        String cachedResult = idempotencyService.check(merchantId, outTradeNo);
        if (cachedResult != null) {
            log.info("[PAY] 幂等命中: merchantId={}, outTradeNo={}", merchantId, outTradeNo);
            throw new DuplicateRequestException(cachedResult);
        }

        // 2. 验签
        signatureService.verify(request, signature, timestamp, nonce, merchantId);

        // 3. 风控检查
        RiskCheckResult riskResult = riskService.check(request, clientIp);
        if (!riskResult.isPassed()) {
            log.warn("[PAY] 风控拦截: merchantId={}, reason={}", merchantId, riskResult.getRejectReason());
            throw new BalanceInsufficientException(/* Phase 4 用独立异常 */
                    java.math.BigDecimal.ZERO, request.getAmount());
        }

        // 4. 渠道路由
        RouteResult route = channelRouterService.route(merchantId, request.getAmount());

        // 5. 调用渠道模拟器
        ChannelPayRequest channelRequest = new ChannelPayRequest();
        channelRequest.setOutTradeNo(outTradeNo);
        channelRequest.setAmount(request.getAmount());
        channelRequest.setChannelType(route.getChannelType());

        ChannelPayResponse channelResponse;
        try {
            channelResponse = channelSimulatorClient.pay(channelRequest);
        } catch (Exception e) {
            // 调用渠道本身超时 → 当作 UNKNOWN，需要查单
            log.error("[PAY] 调用渠道异常，当作 UNKNOWN: outTradeNo={}", outTradeNo, e);
            channelResponse = ChannelPayResponse.builder()
                    .outTradeNo(outTradeNo)
                    .amount(request.getAmount())
                    .status("UNKNOWN")
                    .message("渠道调用超时")
                    .build();
        }

        // 根据渠道返回结果走不同分支
        PayResponse payResponse;
        switch (channelResponse.getStatus()) {
            case "SUCCESS":
                payResponse = handleSuccess(request, channelResponse, route);
                break;
            case "FAIL":
                payResponse = handleFail(request, channelResponse);
                break;
            case "UNKNOWN":
            default:
                payResponse = handleUnknown(request, channelResponse);
                break;
        }

        // 记录幂等结果
        try {
            String resultJson = objectMapper.writeValueAsString(payResponse);
            idempotencyService.save(merchantId, outTradeNo, resultJson);
        } catch (Exception e) {
            log.error("[PAY] 记录幂等结果失败: outTradeNo={}", outTradeNo, e);
        }

        return payResponse;
    }

    /**
     * 支付查询。
     * <p>Phase 1 简化实现：返回缓存的结果。</p>
     * Phase 2 会扩展为查询 Order 表 + 渠道查单。
     */
    @Override
    public PayQueryResponse queryPay(PayQueryRequest request) {
        String cachedResult = idempotencyService.check(
                request.getMerchantId(), request.getOutTradeNo());

        if (cachedResult != null) {
            return PayQueryResponse.builder()
                    .outTradeNo(request.getOutTradeNo())
                    .payStatus("SUCCESS")
                    .build();
        }

        return PayQueryResponse.builder()
                .outTradeNo(request.getOutTradeNo())
                .payStatus("NOT_FOUND")
                .build();
    }

    /**
     * 渠道返回 SUCCESS — TCC 扣款 + MQ 通知。
     */
    private PayResponse handleSuccess(PayRequest request,
                                       ChannelPayResponse channelResponse,
                                       RouteResult route) {
        log.info("[PAY] 支付成功，开始TCC扣款: outTradeNo={}, amount={}",
                request.getOutTradeNo(), request.getAmount());

        TryResponse tryResult = null;
        try {
            // TCC Try：冻结余额
            tryResult = accountClient.tryFreeze(request.getMerchantId(),
                    request.getAmount(), request.getOutTradeNo());

            // TCC Confirm：确认扣款 + 生成复式流水
            accountClient.confirm(tryResult.getTccId());

            // 发送 RocketMQ 支付成功事件（order-service + notification-service 消费）
            orderClient.sendPaySuccessEvent(request.getOutTradeNo(),
                    request.getMerchantId(), request.getAmount(),
                    channelResponse.getChannelOrderNo());

            log.info("[PAY] TCC扣款完成: outTradeNo={}, tccId={}",
                    request.getOutTradeNo(), tryResult.getTccId());

        } catch (Exception e) {
            // TCC 异常补偿：如果 Confirm 失败，执行 Cancel 释放冻结
            log.error("[PAY] TCC扣款异常，执行补偿: outTradeNo={}", request.getOutTradeNo(), e);
            if (tryResult != null) {
                try {
                    accountClient.cancel(tryResult.getTccId());
                    log.info("[PAY] TCC补偿完成: tccId={}", tryResult.getTccId());
                } catch (Exception cancelEx) {
                    log.error("[PAY] TCC补偿失败，需人工处理: tccId={}", tryResult.getTccId(), cancelEx);
                }
            }
            throw new RuntimeException("支付处理失败", e);
        }

        return PayResponse.builder()
                .outTradeNo(request.getOutTradeNo())
                .payStatus("SUCCESS")
                .amount(request.getAmount())
                .channelOrderNo(channelResponse.getChannelOrderNo())
                .paidTime(LocalDateTime.now().toString())
                .build();
    }

    /**
     * 渠道明确返回 FAIL — 直接返回失败。
     */
    private PayResponse handleFail(PayRequest request,
                                    ChannelPayResponse channelResponse) {
        log.warn("[PAY] 支付失败: outTradeNo={}, message={}",
                request.getOutTradeNo(), channelResponse.getMessage());

        throw new ChannelException(
                com.payment.platform.common.constant.PayResultEnum.FAIL,
                channelResponse.getMessage());
    }

    /**
     * 渠道返回 UNKNOWN — 标记 processing，后台轮询查单。
     */
    private PayResponse handleUnknown(PayRequest request,
                                       ChannelPayResponse channelResponse) {
        log.warn("[PAY] 渠道返回 UNKNOWN: outTradeNo={}", request.getOutTradeNo());

        // 启动查单轮询（Phase 1 简化：查 3 次，间隔 2s/5s/10s）
        pollForResult(request.getOutTradeNo());

        // 查单后重新获取结果
        ChannelQueryResponse queryResult = channelSimulatorClient.query(request.getOutTradeNo());
        if (queryResult != null && "SUCCESS".equals(queryResult.getStatus())) {
            return PayResponse.builder()
                    .outTradeNo(request.getOutTradeNo())
                    .payStatus("SUCCESS")
                    .amount(request.getAmount())
                    .channelOrderNo(queryResult.getChannelOrderNo())
                    .paidTime(queryResult.getQueryTime())
                    .build();
        }

        // 三次仍 UNKNOWN → 返回 processing 状态（商户稍后查询）
        return PayResponse.builder()
                .outTradeNo(request.getOutTradeNo())
                .payStatus("PROCESSING")
                .amount(request.getAmount())
                .build();
    }

    /**
     * UNKNOWN 状态轮询查单。
     * <p>Phase 1 简化实现：查 3 次，间隔 2s/5s/10s。</p>
     */
    private void pollForResult(String outTradeNo) {
        long[] delays = {2000, 5000, 10000}; // 2s, 5s, 10s
        for (long delay : delays) {
            try {
                Thread.sleep(delay);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            ChannelQueryResponse result = channelSimulatorClient.query(outTradeNo);
            log.info("[PAY] 查单: outTradeNo={}, status={}", outTradeNo,
                    result != null ? result.getStatus() : "ERROR");
            if (result != null && !"UNKNOWN".equals(result.getStatus())) {
                return; // 查到明确结果
            }
        }
    }
}
