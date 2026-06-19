package com.payment.platform.reconciliation.service.impl;

import com.payment.platform.reconciliation.client.ChannelBillClient;
import com.payment.platform.reconciliation.entity.ReconciliationDiff;
import com.payment.platform.reconciliation.repository.ReconciliationDiffRepository;
import com.payment.platform.reconciliation.service.ReconciliationService;
import com.payment.platform.common.dto.event.BillDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReconciliationServiceImpl implements ReconciliationService {

    private final ReconciliationDiffRepository diffRepository;
    private final ChannelBillClient channelBillClient;

    @Override
    public ReconciliationDiff reconcileSingle(String outTradeNo, Long merchantId,
                                               BigDecimal internalAmount, String channelType) {
        // 幂等：已对账过的跳过
        if (diffRepository.existsByOutTradeNo(outTradeNo)) {
            return null;
        }
        // 简化为直接通过（渠道记录在 DB 中由 simulator 维护）
        // 真实场景会调用 channel-simulator 查单接口比对
        log.debug("[RECON] 实时对账通过: outTradeNo={}", outTradeNo);
        return null;
    }

    @Override
    public List<ReconciliationDiff> dailyReconciliation(String date) {
        List<ReconciliationDiff> diffs = new ArrayList<>();
        List<BillDTO> bills = channelBillClient.download(date);

        for (BillDTO bill : bills) {
            if (diffRepository.existsByOutTradeNo(bill.getOutTradeNo())) {
                continue;
            }
            // 比对：这里简化处理，真实场景会查内部流水表
            ReconciliationDiff diff = ReconciliationDiff.builder()
                    .outTradeNo(bill.getOutTradeNo())
                    .channelAmount(bill.getAmount())
                    .internalAmount(BigDecimal.ZERO)
                    .diffAmount(bill.getAmount())
                    .diffType("CHANNEL_ONLY")
                    .status("PENDING")
                    .createTime(LocalDateTime.now())
                    .build();
            diffRepository.save(diff);
            diffs.add(diff);
        }

        log.info("[RECON] 批处理对账完成: date={}, bills={}, diffs={}", date, bills.size(), diffs.size());
        return diffs;
    }
}
