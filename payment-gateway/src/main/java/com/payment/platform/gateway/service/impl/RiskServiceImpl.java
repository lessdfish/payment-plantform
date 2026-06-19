package com.payment.platform.gateway.service.impl;

import com.payment.platform.common.dto.request.PayRequest;
import com.payment.platform.gateway.dto.RiskCheckResult;
import com.payment.platform.gateway.service.RiskService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

/**
 * 风控服务实现。
 * <p>Phase 1 只做基础检查（单笔限额），Phase 4 扩展 IP 频控、日累计限额、黑名单等。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RiskServiceImpl implements RiskService {

    /** 默认单笔最大金额（元） */
    private static final BigDecimal MAX_SINGLE_AMOUNT = new BigDecimal("50000.00");

    /**
     * 风控检查。
     * <p>Phase 1 检查项：单笔金额上限（5 万元）。</p>
     */
    @Override
    public RiskCheckResult check(PayRequest request) {
        // 检查单笔金额上限
        if (request.getAmount().compareTo(MAX_SINGLE_AMOUNT) > 0) {
            log.warn("[RISK] 单笔金额超限: merchantId={}, amount={}, max={}",
                    request.getMerchantId(), request.getAmount(), MAX_SINGLE_AMOUNT);
            return RiskCheckResult.builder()
                    .passed(false)
                    .rejectReason("单笔交易金额超过上限 " + MAX_SINGLE_AMOUNT + " 元")
                    .build();
        }

        // 金额必须为正数（DTO 层已有 @DecimalMin 校验，此处为二次确认）
        if (request.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            return RiskCheckResult.builder()
                    .passed(false)
                    .rejectReason("交易金额必须大于 0")
                    .build();
        }

        return RiskCheckResult.builder().passed(true).build();
    }
}
