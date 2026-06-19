package com.payment.platform.account.service.impl;

import cn.hutool.core.util.IdUtil;
import com.payment.platform.account.entity.JournalEntry;
import com.payment.platform.account.repository.JournalEntryRepository;
import com.payment.platform.account.service.JournalService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 复式记账实现。
 * <p>每笔交易生成两条流水：借方（钱从哪出）+ 贷方（钱到哪去），金额相等。</p>
 */
@Service
@RequiredArgsConstructor
public class JournalServiceImpl implements JournalService {

    private final JournalEntryRepository journalEntryRepository;

    @Override
    public void record(String txnId, Long debitAccountId, Long creditAccountId,
                       BigDecimal amount, String txnType, Long merchantId) {
        LocalDateTime now = LocalDateTime.now();

        // 借方流水：付款方资产减少
        JournalEntry debit = JournalEntry.builder()
                .id(IdUtil.getSnowflake(2, 1).nextId())
                .txnId(txnId)
                .debitAccountId(debitAccountId)
                .creditAccountId(creditAccountId)
                .amount(amount)
                .drCrFlag("D")
                .txnType(txnType)
                .txnTime(now)
                .merchantId(merchantId)
                .build();

        // 贷方流水：收款方资产增加
        JournalEntry credit = JournalEntry.builder()
                .id(IdUtil.getSnowflake(2, 1).nextId())
                .txnId(txnId)
                .debitAccountId(debitAccountId)
                .creditAccountId(creditAccountId)
                .amount(amount)
                .drCrFlag("C")
                .txnType(txnType)
                .txnTime(now)
                .merchantId(merchantId)
                .build();

        journalEntryRepository.save(debit);
        journalEntryRepository.save(credit);
    }
}
