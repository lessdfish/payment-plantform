package com.payment.platform.gateway.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 账户服务 REST 客户端（Phase 2 实现，当前为桩）。
 * <p>Phase 1 的支付链路暂时跳过 TCC 账户扣款，仅验证网关→渠道链路。</p>
 */
@Slf4j
@Component
public class AccountClient {

    /**
     * Phase 2 实现：TCC Try 阶段 — 冻结商户余额。
     */
    public void tryFreeze(Long merchantId, java.math.BigDecimal amount, String bizOrderNo) {
        log.info("[ACCOUNT-STUB] TCC Try: merchantId={}, amount={}, bizOrderNo={}", merchantId, amount, bizOrderNo);
    }

    /**
     * Phase 2 实现：TCC Confirm 阶段 — 确认扣款。
     */
    public void confirm(String tccId) {
        log.info("[ACCOUNT-STUB] TCC Confirm: tccId={}", tccId);
    }

    /**
     * Phase 2 实现：TCC Cancel 阶段 — 释放冻结。
     */
    public void cancel(String tccId) {
        log.info("[ACCOUNT-STUB] TCC Cancel: tccId={}", tccId);
    }
}
