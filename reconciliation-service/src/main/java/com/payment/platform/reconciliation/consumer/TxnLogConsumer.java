package com.payment.platform.reconciliation.consumer;

import com.payment.platform.common.dto.event.TxnLogEvent;
import com.payment.platform.reconciliation.service.ReconciliationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Canal 实时对账消费者 — 消费 Kafka txn-log topic。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TxnLogConsumer {

    private final ReconciliationService reconciliationService;

    @KafkaListener(topics = "txn-log", groupId = "reconciliation-txn-log")
    public void onMessage(TxnLogEvent event) {
        log.debug("[RECON-CONSUMER] 收到 txn-log: txnId={}", event.getTxnId());
        reconciliationService.reconcileSingle(
                event.getTxnId(), event.getMerchantId(),
                event.getAmount(), event.getTxnType());
    }
}
