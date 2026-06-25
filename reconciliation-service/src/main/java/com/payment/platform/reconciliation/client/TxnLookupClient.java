package com.payment.platform.reconciliation.client;

import com.payment.platform.common.dto.event.TxnLogEvent;
import com.payment.platform.common.result.ApiResult;
import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;

@Component
public class TxnLookupClient {

    @Value("${services.account.base-url:http://localhost:8081}")
    private String baseUrl;

    private final RestClient restClient = RestClient.create();

    public TxnLogEvent lookup(String txnId, Long merchantId) {
        ApiResult<TxnData> result = restClient.get()
                .uri(baseUrl + "/api/v1/account/transaction/{txnId}?merchantId={merchantId}",
                        txnId, merchantId)
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});
        if (result == null || result.getData() == null) {
            return null;
        }
        TxnData txn = result.getData();
        return TxnLogEvent.builder()
                .txnId(txn.getTxnId())
                .merchantId(txn.getMerchantId())
                .amount(txn.getAmount())
                .txnType(txn.getTxnType())
                .outTradeNo(txn.getOutTradeNo())
                .txnTime(System.currentTimeMillis())
                .build();
    }

    @Data
    public static class TxnData {
        private String txnId;
        private Long merchantId;
        private BigDecimal amount;
        private String txnType;
        private String outTradeNo;
    }
}
