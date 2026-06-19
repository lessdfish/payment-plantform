package com.payment.platform.reconciliation.service;

import com.payment.platform.reconciliation.entity.ReconciliationDiff;

import java.util.List;

/**
 * 对账服务接口。
 */
public interface ReconciliationService {

    /**
     * 实时对账：比对单笔内部流水与渠道记录。
     * @return 如果不一致返回差异对象，一致返回 null
     */
    ReconciliationDiff reconcileSingle(String outTradeNo, Long merchantId,
                                        java.math.BigDecimal internalAmount, String channelType);

    /**
     * 批处理对账：下载渠道账单并逐笔比对。
     * @return 差异列表
     */
    List<ReconciliationDiff> dailyReconciliation(String date);
}
