package com.payment.platform.reconciliation.client;

import com.payment.platform.common.dto.event.BillDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import com.payment.platform.common.dto.response.ChannelQueryResponse;
import java.util.Collections;
import java.util.List;

/**
 * 渠道账单客户端 — 调用 channel-simulator 获取 T-1 账单。
 */
@Slf4j
@Component
public class ChannelBillClient {
    @Value("${services.channel-simulator.base-url:http://localhost:8086}")
    private String baseUrl;
    private final RestClient client = RestClient.create();
    private static final ParameterizedTypeReference<com.payment.platform.common.result.ApiResult<List<BillDTO>>> BILL_TYPE =
            new ParameterizedTypeReference<>() {};

    public List<BillDTO> download(String date) {
        try {
            var result = client.get()
                    .uri(baseUrl + "/api/v1/simulator/bill/{date}", date)
                    .retrieve()
                    .body(BILL_TYPE);
            return result != null && result.getData() != null ? result.getData() : Collections.emptyList();
        } catch (Exception e) {
            log.error("[BILL-CLIENT] 账单下载失败: date={}", date, e);
            return Collections.emptyList();
        }
    }

    public ChannelQueryResponse query(String outTradeNo) {
        try {
            var result = client.get()
                    .uri(baseUrl + "/api/v1/simulator/query?outTradeNo={outTradeNo}", outTradeNo)
                    .retrieve()
                    .body(new ParameterizedTypeReference<com.payment.platform.common.result.ApiResult<ChannelQueryResponse>>() {});
            return result == null ? null : result.getData();
        } catch (Exception e) {
            log.error("[BILL-CLIENT] 渠道查单失败: outTradeNo={}", outTradeNo, e);
            return null;
        }
    }
}
