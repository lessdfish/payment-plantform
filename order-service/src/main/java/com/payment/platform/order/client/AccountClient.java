package com.payment.platform.order.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;

@Component
public class AccountClient {

    @Value("${services.account.base-url:http://localhost:8081}")
    private String baseUrl;

    private final RestClient restClient = RestClient.create();

    public void refund(Long merchantId, BigDecimal amount, String outRefundNo) {
        restClient.post()
                .uri(baseUrl + "/api/v1/account/refund/{merchantId}"
                                + "?amount={amount}&outRefundNo={outRefundNo}",
                        merchantId, amount, outRefundNo)
                .retrieve()
                .toBodilessEntity();
    }
}
