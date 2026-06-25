package com.payment.platform.reconciliation.consumer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.payment.platform.common.dto.event.TxnLogEvent;
import com.payment.platform.reconciliation.client.TxnLookupClient;
import com.payment.platform.reconciliation.service.ReconciliationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * Canal 实时对账消费者 — 消费 Kafka txn-log topic。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TxnLogConsumer {

    private final ReconciliationService reconciliationService;
    private final TxnLookupClient txnLookupClient;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "txn-log", groupId = "reconciliation-txn-log-v2")
    public void onMessage(String payload) {
        try {
            JsonNode root = objectMapper.readTree(payload);
            if (root.hasNonNull("txnId")) {
                reconcile(objectMapper.treeToValue(root, TxnLogEvent.class));
                return;
            }
            if (!root.has("data") || !root.path("table").asText().startsWith("journal_entry_")) {
                return;
            }
            for (JsonNode row : root.path("data")) {
                if (!"D".equals(row.path("dr_cr_flag").asText())) {
                    continue;
                }
                String txnId = row.path("txn_id").asText();
                Long merchantId = row.path("merchant_id").asLong();
                String outTradeNo = row.path("out_trade_no").asText();
                if (!outTradeNo.isBlank()) {
                    reconcile(TxnLogEvent.builder()
                            .txnId(txnId)
                            .merchantId(merchantId)
                            .amount(new BigDecimal(row.path("amount").asText()))
                            .txnType(row.path("txn_type").asText())
                            .outTradeNo(outTradeNo)
                            .txnTime(System.currentTimeMillis())
                            .build());
                    continue;
                }
                TxnLogEvent event = txnLookupClient.lookup(txnId, merchantId);
                if (event != null) {
                    reconcile(event);
                }
            }
        } catch (Exception e) {
            log.error("[RECON-CONSUMER] 无法解析 txn-log: payload={}", payload, e);
            throw new RuntimeException(e);
        }
    }

    private void reconcile(TxnLogEvent event) {
        log.debug("[RECON-CONSUMER] 收到 txn-log: txnId={}", event.getTxnId());
        reconciliationService.reconcileSingle(
                event.getOutTradeNo(), event.getMerchantId(),
                event.getAmount(), event.getTxnType());
    }
}
