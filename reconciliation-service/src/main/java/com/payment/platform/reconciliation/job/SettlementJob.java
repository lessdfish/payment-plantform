package com.payment.platform.reconciliation.job;

import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 日终清算任务（当前为骨架，Phase 5 完善）。
 */
@Slf4j
@Component
public class SettlementJob {

    @XxlJob("dailySettlementJob")
    public void execute() {
        log.info("[XXL-JOB] 日终清算任务执行（预留）");
    }
}
