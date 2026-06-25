package com.payment.platform.gateway.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.payment.platform.common.dto.request.PayQueryRequest;
import com.payment.platform.common.dto.request.PayRequest;
import com.payment.platform.common.dto.response.PayQueryResponse;
import com.payment.platform.common.dto.response.PayResponse;
import com.payment.platform.common.exception.BusinessException;
import com.payment.platform.common.exception.ChannelException;
import com.payment.platform.common.exception.DuplicateRequestException;
import com.payment.platform.common.exception.SignatureException;
import com.payment.platform.gateway.client.AccountClient;
import com.payment.platform.gateway.client.ChannelSimulatorClient;
import com.payment.platform.gateway.client.OrderClient;
import com.payment.platform.gateway.client.PayProcessProducer;
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
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.function.Supplier;

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
public class PayServiceImpl implements PayService {

    private final IdempotencyService idempotencyService;
    private final SignatureService signatureService;
    private final RiskService riskService;
    private final ChannelRouterService channelRouterService;
    private final ChannelSimulatorClient channelSimulatorClient;
    private final AccountClient accountClient;
    private final OrderClient orderClient;
    private final PayProcessProducer payProcessProducer;
    private final ObjectMapper objectMapper;
    private final TaskExecutor paymentPollExecutor;
    private final MeterRegistry meterRegistry;
    private final boolean asyncEnabled;

    public PayServiceImpl(IdempotencyService idempotencyService,
                          SignatureService signatureService,
                          RiskService riskService,
                          ChannelRouterService channelRouterService,
                          ChannelSimulatorClient channelSimulatorClient,
                          AccountClient accountClient,
                          OrderClient orderClient,
                          PayProcessProducer payProcessProducer,
                          ObjectMapper objectMapper,
                          @Qualifier("paymentPollExecutor") TaskExecutor paymentPollExecutor,
                          MeterRegistry meterRegistry,
                          @Value("${payment.async.enabled:true}") boolean asyncEnabled) {
        this.idempotencyService = idempotencyService;
        this.signatureService = signatureService;
        this.riskService = riskService;
        this.channelRouterService = channelRouterService;
        this.channelSimulatorClient = channelSimulatorClient;
        this.accountClient = accountClient;
        this.orderClient = orderClient;
        this.payProcessProducer = payProcessProducer;
        this.objectMapper = objectMapper;
        this.paymentPollExecutor = paymentPollExecutor;
        this.meterRegistry = meterRegistry;
        this.asyncEnabled = asyncEnabled;
    }

    /**
     * 支付下单。
     * <p>同步返回支付结果。渠道返回 UNKNOWN 时异步轮询查单。</p>
     */
    @Override
    public PayResponse createPay(PayRequest request, String signature,
                                  String timestamp, String nonce, String clientIp) {
        Long merchantId = request.getMerchantId();
        String outTradeNo = request.getOutTradeNo();

        String processingJson;
        try {
            processingJson = objectMapper.writeValueAsString(PayResponse.builder()
                    .outTradeNo(outTradeNo)
                    .payStatus("PROCESSING")
                    .amount(request.getAmount())
                    .build());
        } catch (Exception e) {
            throw new IllegalStateException("构造幂等占位失败", e);
        }

        boolean reservationAcquired = false;
        try {
            // 1. 验签
            signatureService.verify(request, signature, timestamp, nonce, merchantId);

            // 2. 风控检查
            RiskCheckResult riskResult = riskService.check(request, clientIp);
            if (!riskResult.isPassed()) {
                log.warn("[PAY] 风控拦截: merchantId={}, reason={}", merchantId, riskResult.getRejectReason());
                throw new BusinessException(riskResult.getErrorCode(), riskResult.getRejectReason());
            }

            // 3. 原子幂等占位
            String cachedResult = timed("idempotency.reserve", () ->
                    idempotencyService.reserve(
                            merchantId, outTradeNo, processingJson));
            if (cachedResult != null) {
                log.info("[PAY] 幂等命中: merchantId={}, outTradeNo={}", merchantId, outTradeNo);
                throw new DuplicateRequestException(cachedResult);
            }
            reservationAcquired = true;

            if (asyncEnabled) {
                timed("event.accept", () -> payProcessProducer.send(request));
                return PayResponse.builder()
                        .outTradeNo(outTradeNo)
                        .payStatus("PROCESSING")
                        .amount(request.getAmount())
                        .build();
            }

            PayResponse payResponse = executePayment(request, false);
            String resultJson = objectMapper.writeValueAsString(payResponse);
            timed("idempotency.save", () ->
                    idempotencyService.save(merchantId, outTradeNo, resultJson));

            return payResponse;
        } catch (RuntimeException e) {
            if (reservationAcquired) {
                idempotencyService.release(merchantId, outTradeNo);
            }
            throw e;
        } catch (Exception e) {
            if (reservationAcquired) {
                idempotencyService.release(merchantId, outTradeNo);
            }
            throw new RuntimeException("支付处理失败", e);
        }
    }

    @Override
    public void processAccepted(PayRequest request) {
        String existing = idempotencyService.check(
                request.getMerchantId(), request.getOutTradeNo());
        if (isFinalResult(existing)) {
            return;
        }

        try {
            PayResponse response = executePayment(request, true);
            idempotencyService.save(
                    request.getMerchantId(),
                    request.getOutTradeNo(),
                    objectMapper.writeValueAsString(response));
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException(
                    "异步支付结果保存失败: " + request.getOutTradeNo(), e);
        }
    }

    private PayResponse executePayment(PayRequest request, boolean asyncWorker) {
        RouteResult route = timed("route", () ->
                channelRouterService.route(
                        request.getMerchantId(), request.getAmount()));

        ChannelPayRequest channelRequest = new ChannelPayRequest();
        channelRequest.setOutTradeNo(request.getOutTradeNo());
        channelRequest.setAmount(request.getAmount());
        channelRequest.setChannelType(route.getChannelType());

        ChannelPayResponse channelResponse;
        try {
            channelResponse = timed("channel.pay", () ->
                    channelSimulatorClient.pay(channelRequest));
        } catch (Exception e) {
            log.error("[PAY] 调用渠道异常，当作 UNKNOWN: outTradeNo={}",
                    request.getOutTradeNo(), e);
            channelResponse = ChannelPayResponse.builder()
                    .outTradeNo(request.getOutTradeNo())
                    .amount(request.getAmount())
                    .status("UNKNOWN")
                    .message("渠道调用超时")
                    .build();
        }

        return switch (channelResponse.getStatus()) {
            case "SUCCESS" -> handleSuccess(request, channelResponse);
            case "FAIL" -> asyncWorker
                    ? PayResponse.builder()
                            .outTradeNo(request.getOutTradeNo())
                            .payStatus("FAIL")
                            .amount(request.getAmount())
                            .build()
                    : handleFail(request, channelResponse);
            case "UNKNOWN" -> handleUnknown(request);
            default -> handleUnknown(request);
        };
    }

    private boolean isFinalResult(String resultJson) {
        if (resultJson == null) {
            return false;
        }
        try {
            String status = objectMapper.readValue(
                    resultJson, PayResponse.class).getPayStatus();
            return "SUCCESS".equals(status) || "FAIL".equals(status);
        } catch (Exception e) {
            return false;
        }
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
            try {
                PayResponse cached = objectMapper.readValue(cachedResult, PayResponse.class);
                return PayQueryResponse.builder()
                        .outTradeNo(cached.getOutTradeNo())
                        .payStatus(cached.getPayStatus())
                        .amount(cached.getAmount())
                        .build();
            } catch (Exception e) {
                log.error("[PAY] 幂等结果反序列化失败: outTradeNo={}",
                        request.getOutTradeNo(), e);
            }
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
                                       ChannelPayResponse channelResponse) {
        log.info("[PAY] 支付成功，开始TCC扣款: outTradeNo={}, amount={}",
                request.getOutTradeNo(), request.getAmount());

        try {
            // 账户服务内连续执行 TCC Try/Confirm，减少一次跨服务 HTTP 往返。
            TryResponse tryResult = timed("account.execute", () ->
                    accountClient.executePayment(request.getMerchantId(),
                            request.getAmount(), request.getOutTradeNo()));

            // 发送 RocketMQ 支付成功事件（order-service + notification-service 消费）
            try {
                timed("event.publish", () ->
                        orderClient.sendPaySuccessEvent(request.getOutTradeNo(),
                                request.getMerchantId(), request.getAmount(),
                                channelResponse.getChannelOrderNo(), request.getNotifyUrl()));
            } catch (Exception mqEx) {
                log.error("[PAY] 支付已确认，支付成功事件发送失败: outTradeNo={}",
                        request.getOutTradeNo(), mqEx);
            }

            log.info("[PAY] TCC扣款完成: outTradeNo={}, tccId={}",
                    request.getOutTradeNo(), tryResult.getTccId());

        } catch (Exception e) {
            // execute 接口在 Confirm 失败时已于账户服务内执行 Cancel。
            log.error("[PAY] TCC扣款异常: outTradeNo={}", request.getOutTradeNo(), e);
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
    private PayResponse handleUnknown(PayRequest request) {
        log.warn("[PAY] 渠道返回 UNKNOWN: outTradeNo={}", request.getOutTradeNo());
        paymentPollExecutor.execute(() -> pollForResult(request));
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
    private void pollForResult(PayRequest request) {
        String outTradeNo = request.getOutTradeNo();
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
            if (result == null || "UNKNOWN".equals(result.getStatus())) {
                continue;
            }
            try {
                PayResponse finalResponse;
                if ("SUCCESS".equals(result.getStatus())) {
                    ChannelPayResponse channelResponse = ChannelPayResponse.builder()
                            .outTradeNo(outTradeNo)
                            .amount(request.getAmount())
                            .channelOrderNo(result.getChannelOrderNo())
                            .status("SUCCESS")
                            .build();
                    finalResponse = handleSuccess(request, channelResponse);
                } else {
                    finalResponse = PayResponse.builder()
                            .outTradeNo(outTradeNo)
                            .payStatus("FAIL")
                            .amount(request.getAmount())
                            .build();
                }
                idempotencyService.save(request.getMerchantId(), outTradeNo,
                        objectMapper.writeValueAsString(finalResponse));
            } catch (Exception e) {
                log.error("[PAY] UNKNOWN 查单结果处理失败: outTradeNo={}", outTradeNo, e);
            }
            return;
        }
    }

    private <T> T timed(String stage, Supplier<T> action) {
        return Timer.builder("payment.stage")
                .tag("stage", stage)
                .register(meterRegistry)
                .record(action);
    }

    private void timed(String stage, Runnable action) {
        Timer.builder("payment.stage")
                .tag("stage", stage)
                .register(meterRegistry)
                .record(action);
    }
}
