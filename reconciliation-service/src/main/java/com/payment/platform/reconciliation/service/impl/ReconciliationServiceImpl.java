package com.payment.platform.reconciliation.service.impl;

import com.payment.platform.reconciliation.client.ChannelBillClient;
import com.payment.platform.reconciliation.entity.ReconciliationDiff;
import com.payment.platform.reconciliation.repository.ReconciliationDiffRepository;
import com.payment.platform.reconciliation.entity.ReconciliationRecord;
import com.payment.platform.reconciliation.repository.ReconciliationRecordRepository;
import com.payment.platform.reconciliation.service.ReconciliationService;
import com.payment.platform.common.dto.event.BillDTO;
import com.payment.platform.common.dto.response.ChannelQueryResponse;
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
    private final ReconciliationRecordRepository recordRepository;
    private final ChannelBillClient channelBillClient;

    @Override
    public ReconciliationDiff reconcileSingle(String outTradeNo, Long merchantId,
                                               BigDecimal internalAmount, String channelType) {
        // 幂等：已对账过的跳过
        if (diffRepository.existsByOutTradeNo(outTradeNo)) {
            return null;
        }
        ReconciliationRecord record = recordRepository.findByOutTradeNo(outTradeNo)
                .orElseGet(() -> ReconciliationRecord.builder()
                        .outTradeNo(outTradeNo)
                        .merchantId(merchantId)
                        .internalAmount(internalAmount)
                        .status("PENDING")
                        .createTime(LocalDateTime.now())
                        .build());

        ChannelQueryResponse channel = channelBillClient.query(outTradeNo);
        ReconciliationDiff diff = compare(outTradeNo, merchantId, internalAmount,
                channel == null ? null : channel.getAmount(),
                channel == null ? null : channel.getStatus());
        record.setStatus(diff == null ? "MATCHED" : "MISMATCH");
        recordRepository.save(record);
        return diff;
    }

    @Override
    public List<ReconciliationDiff> dailyReconciliation(String date) {
        List<ReconciliationDiff> diffs = new ArrayList<>();
        List<BillDTO> bills = channelBillClient.download(date);

        for (BillDTO bill : bills) {
            if (diffRepository.existsByOutTradeNo(bill.getOutTradeNo())) {
                continue;
            }
            ReconciliationRecord record = recordRepository.findByOutTradeNo(bill.getOutTradeNo())
                    .orElse(null);
            ReconciliationDiff diff = compare(
                    bill.getOutTradeNo(),
                    record == null ? 0L : record.getMerchantId(),
                    record == null ? null : record.getInternalAmount(),
                    bill.getAmount(),
                    bill.getStatus());
            if (diff != null) {
                diffs.add(diff);
            }
        }

        log.info("[RECON] 批处理对账完成: date={}, bills={}, diffs={}", date, bills.size(), diffs.size());
        return diffs;
    }

    private ReconciliationDiff compare(String outTradeNo, Long merchantId,
                                       BigDecimal internalAmount,
                                       BigDecimal channelAmount,
                                       String channelStatus) {
        String diffType = null;
        if (internalAmount == null) {
            diffType = "CHANNEL_ONLY";
            internalAmount = BigDecimal.ZERO;
        } else if (channelAmount == null || !"SUCCESS".equals(channelStatus)) {
            diffType = "INTERNAL_ONLY";
            channelAmount = BigDecimal.ZERO;
        } else if (internalAmount.compareTo(channelAmount) != 0) {
            diffType = "AMOUNT_MISMATCH";
        }
        if (diffType == null) {
            log.debug("[RECON] 对账通过: outTradeNo={}", outTradeNo);
            return null;
        }

        ReconciliationDiff diff = ReconciliationDiff.builder()
                .outTradeNo(outTradeNo)
                .merchantId(merchantId)
                .internalAmount(internalAmount)
                .channelAmount(channelAmount)
                .diffAmount(channelAmount.subtract(internalAmount))
                .diffType(diffType)
                .status("PENDING")
                .createTime(LocalDateTime.now())
                .build();
        return diffRepository.save(diff);
    }
}
