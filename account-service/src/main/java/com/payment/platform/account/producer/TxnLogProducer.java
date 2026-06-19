package com.payment.platform.account.producer;

import com.payment.platform.common.dto.event.TxnLogEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * 交易流水 Kafka 生产者（Phase 3 对账使用，当前预留）。
 * <p>每次 TCC Confirm 后发送流水到 Kafka txn-log topic，
 * 供 reconciliation-service 消费进行实时对账。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TxnLogProducer {

    private final KafkaTemplate<String, TxnLogEvent> kafkaTemplate;

    /**
     * 发送交易流水到 Kafka（Phase 3 启用）。
     */
    public void send(TxnLogEvent event) {
        try {
            kafkaTemplate.send("txn-log", event.getTxnId(), event);
            log.debug("[KAFKA] 发送 txn-log: txnId={}", event.getTxnId());
        } catch (Exception e) {
            // Kafka 不可用时仅记录日志，不阻塞主流程
            log.warn("[KAFKA] 发送 txn-log 失败（不阻塞主流程）: txnId={}", event.getTxnId(), e);
        }
    }
}
