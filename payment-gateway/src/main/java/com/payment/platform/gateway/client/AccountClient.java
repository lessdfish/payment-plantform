package com.payment.platform.gateway.client;

import com.payment.platform.common.dto.request.CancelRequest;
import com.payment.platform.common.dto.request.ConfirmRequest;
import com.payment.platform.common.dto.request.TryRequest;
import com.payment.platform.common.dto.response.TryResponse;
import com.payment.platform.common.result.ApiResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;

/**
 * 账户服务 REST 客户端 — 调用 TCC 三阶段接口。
 */
@Slf4j
@Component
public class AccountClient {

    private static final String BASE_URL = "http://localhost:8081";
    private final RestClient client = RestClient.create();
    private static final ParameterizedTypeReference<ApiResult<TryResponse>> TRY_TYPE =
            new ParameterizedTypeReference<>() {};

    public TryResponse tryFreeze(Long merchantId, BigDecimal amount, String bizOrderNo) {
        TryRequest req = new TryRequest();
        req.setMerchantId(merchantId);
        req.setAmount(amount);
        req.setBizOrderNo(bizOrderNo);

        ApiResult<TryResponse> result = client.post()
                .uri(BASE_URL + "/api/v1/account/tcc/try")
                .body(req)
                .retrieve()
                .body(TRY_TYPE);
        if (result == null || result.getData() == null) {
            throw new RuntimeException("TCC Try 失败");
        }
        log.info("[ACCOUNT-CLIENT] TCC Try 成功: tccId={}", result.getData().getTccId());
        return result.getData();
    }

    public void confirm(String tccId) {
        ConfirmRequest req = new ConfirmRequest();
        req.setTccId(tccId);
        client.post().uri(BASE_URL + "/api/v1/account/tcc/confirm").body(req).retrieve().toBodilessEntity();
        log.info("[ACCOUNT-CLIENT] TCC Confirm 成功: tccId={}", tccId);
    }

    public void cancel(String tccId) {
        CancelRequest req = new CancelRequest();
        req.setTccId(tccId);
        client.post().uri(BASE_URL + "/api/v1/account/tcc/cancel").body(req).retrieve().toBodilessEntity();
        log.info("[ACCOUNT-CLIENT] TCC Cancel 成功: tccId={}", tccId);
    }

    public void recharge(Long merchantId, BigDecimal amount, String outTradeNo) {
        client.post()
                .uri(BASE_URL + "/api/v1/account/recharge/{merchantId}?amount={amount}&outTradeNo={outTradeNo}",
                        merchantId, amount, outTradeNo)
                .retrieve().toBodilessEntity();
        log.info("[ACCOUNT-CLIENT] 充值成功: merchantId={}, amount={}", merchantId, amount);
    }
}
