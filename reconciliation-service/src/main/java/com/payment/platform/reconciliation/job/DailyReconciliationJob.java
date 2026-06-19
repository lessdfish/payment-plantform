package com.payment.platform.reconciliation.job;

import com.payment.platform.reconciliation.entity.ReconciliationDiff;
import com.payment.platform.reconciliation.service.ReconciliationService;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 每日批处理对账任务（XXL-JOB 调度）。
 * <p>每天凌晨 2:00 执行，拉取 T-1 日渠道账单与内部流水逐笔比对。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DailyReconciliationJob {

    private final ReconciliationService reconciliationService;

    /**
     * 日终对账主流程。
     */
    @XxlJob("dailyReconciliationJob")
    public void execute() {
        String yesterday = LocalDate.now().minusDays(1).format(DateTimeFormatter.ISO_LOCAL_DATE);
        log.info("[XXL-JOB] 开始日终对账: date={}", yesterday);
        List<ReconciliationDiff> diffs = reconciliationService.dailyReconciliation(yesterday);
        log.info("[XXL-JOB] 日终对账完成: 发现差异 {} 笔", diffs.size());
    }
}
