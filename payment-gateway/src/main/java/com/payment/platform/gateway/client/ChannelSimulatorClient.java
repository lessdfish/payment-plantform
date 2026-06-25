package com.payment.platform.gateway.client;

import com.payment.platform.common.dto.request.ChannelPayRequest;
import com.payment.platform.common.dto.response.ChannelPayResponse;
import com.payment.platform.common.dto.response.ChannelQueryResponse;
import com.payment.platform.common.result.ApiResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * 渠道模拟器 REST 客户端。
 * <p>直连 channel-simulator（localhost:8086），后续切换为 Nacos 服务发现。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChannelSimulatorClient {

    @Value("${services.channel-simulator.base-url:http://localhost:8086}")
    private String baseUrl;

    private final RestClient client;

    private static final ParameterizedTypeReference<ApiResult<ChannelPayResponse>> PAY_TYPE =
            new ParameterizedTypeReference<>() {};
    private static final ParameterizedTypeReference<ApiResult<ChannelQueryResponse>> QUERY_TYPE =
            new ParameterizedTypeReference<>() {};

    /**
     * 调用渠道模拟器的支付接口。
     */
    public ChannelPayResponse pay(ChannelPayRequest request) {
        ApiResult<ChannelPayResponse> result = client.post()
                .uri(baseUrl + "/api/v1/simulator/pay")
                .body(request)
                .retrieve()
                .body(PAY_TYPE);

        if (result == null || result.getData() == null) {
            log.error("[CHANNEL-CLIENT] 渠道支付响应异常");
            throw new RuntimeException("渠道支付接口返回异常");
        }
        return result.getData();
    }

    /**
     * 调用渠道模拟器的查单接口。
     */
    public ChannelQueryResponse query(String outTradeNo) {
        ApiResult<ChannelQueryResponse> result = client.get()
                .uri(baseUrl + "/api/v1/simulator/query?outTradeNo=" + outTradeNo)
                .retrieve()
                .body(QUERY_TYPE);

        if (result == null || result.getData() == null) {
            log.warn("[CHANNEL-CLIENT] 查单无结果: outTradeNo={}", outTradeNo);
            return null;
        }
        return result.getData();
    }
}
