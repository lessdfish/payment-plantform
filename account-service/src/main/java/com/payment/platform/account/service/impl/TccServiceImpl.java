package com.payment.platform.account.service.impl;

import cn.hutool.core.util.IdUtil;
import com.payment.platform.common.dto.request.CancelRequest;
import com.payment.platform.common.dto.request.ConfirmRequest;
import com.payment.platform.common.dto.request.TryRequest;
import com.payment.platform.common.dto.response.TryResponse;
import com.payment.platform.account.entity.Account;
import com.payment.platform.account.entity.Transaction;
import com.payment.platform.account.repository.AccountRepository;
import com.payment.platform.account.repository.TransactionRepository;
import com.payment.platform.account.service.JournalService;
import com.payment.platform.account.service.TccService;
import com.payment.platform.common.exception.BalanceInsufficientException;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * TCC 分布式事务实现。
 * <p><b>核心设计：</b></p>
 * <ul>
 *   <li>Try: 冻结余额（balance 不变，frozenAmount 增加）</li>
 *   <li>Confirm: 实扣余额 + 生成复式流水（借-贷两条）</li>
 *   <li>Cancel: 释放冻结（frozenAmount 减少，balance 不变）</li>
 * </ul>
 *
 * <p><b>幂等机制：</b>每个阶段通过 txnId 查询 transaction 表，已处理则直接返回。</p>
 * <p><b>防超卖：</b>Try 阶段校验 balance - frozenAmount >= amount，不足则拒绝。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TccServiceImpl implements TccService {

    /** 平台收入账户 ID（固定，所有手续费和收入归集到此） */
    private static final Long PLATFORM_ACCOUNT_ID = 0L;

    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final JournalService journalService;

    @Override
    @Transactional
    public TryResponse tryFreeze(TryRequest request) {
        Long merchantId = request.getMerchantId();
        BigDecimal amount = request.getAmount();
        String tccId = "TCC" + System.currentTimeMillis() + IdUtil.fastSimpleUUID().substring(0, 6);

        // 幂等检查
        if (transactionRepository.findByOutTradeNoAndTxnType(request.getBizOrderNo(), "FREEZE").isPresent()) {
            log.info("[TCC-TRY] 幂等命中: bizOrderNo={}", request.getBizOrderNo());
            Transaction existing = transactionRepository
                    .findByOutTradeNoAndTxnType(request.getBizOrderNo(), "FREEZE").get();
            return TryResponse.builder()
                    .tccId(existing.getTxnId())
                    .frozenAmount(amount)
                    .build();
        }

        // 查询账户，校验可用余额
        Account account = accountRepository.findByMerchantId(merchantId)
                .orElseThrow(() -> {
                    log.error("[TCC-TRY] 账户不存在: merchantId={}", merchantId);
                    return new EntityNotFoundException("账户不存在: " + merchantId);
                });

        // 防超卖：可用余额 = balance - frozenAmount
        BigDecimal available = account.getAvailableBalance();
        if (available.compareTo(amount) < 0) {
            log.warn("[TCC-TRY] 余额不足: merchantId={}, available={}, required={}",
                    merchantId, available, amount);
            throw new BalanceInsufficientException(available, amount);
        }

        // 冻结：只增加 frozenAmount，不扣 balance
        account.setFrozenAmount(account.getFrozenAmount().add(amount));
        accountRepository.save(account);

        // 记录交易
        Transaction txn = Transaction.builder()
                .id(IdUtil.getSnowflake(2, 1).nextId())
                .txnId(tccId)
                .merchantId(merchantId)
                .amount(amount)
                .txnType("FREEZE")
                .outTradeNo(request.getBizOrderNo())
                .status("SUCCESS")
                .createTime(LocalDateTime.now())
                .build();
        transactionRepository.save(txn);

        log.info("[TCC-TRY] 冻结成功: tccId={}, merchantId={}, amount={}, frozenAfter={}",
                tccId, merchantId, amount, account.getFrozenAmount());

        return TryResponse.builder()
                .tccId(tccId)
                .frozenAmount(amount)
                .build();
    }

    @Override
    @Transactional
    public void confirm(ConfirmRequest request) {
        String tccId = request.getTccId();

        // 幂等：已 Confirm 过直接返回
        if (transactionRepository.findByTxnId(tccId + "_CONFIRM").isPresent()) {
            log.info("[TCC-CONFIRM] 幂等命中: tccId={}", tccId);
            return;
        }

        // 找到对应的 Try 记录
        Transaction tryTxn = transactionRepository.findByTxnId(tccId)
                .orElseThrow(() -> new RuntimeException("TCC Try 记录不存在: " + tccId));

        Long merchantId = tryTxn.getMerchantId();
        BigDecimal amount = tryTxn.getAmount();

        // 获取账户
        Account account = accountRepository.findByMerchantId(merchantId)
                .orElseThrow(() -> new EntityNotFoundException("账户不存在"));

        // 实扣：balance 减少，frozenAmount 减少
        account.setBalance(account.getBalance().subtract(amount));
        account.setFrozenAmount(account.getFrozenAmount().subtract(amount));
        accountRepository.save(account);

        // 记录 Confirm 交易（幂等键）
        Transaction confirmTxn = Transaction.builder()
                .id(IdUtil.getSnowflake(2, 1).nextId())
                .txnId(tccId + "_CONFIRM")
                .merchantId(merchantId)
                .amount(amount)
                .txnType("PAY")
                .outTradeNo(tryTxn.getOutTradeNo())
                .status("SUCCESS")
                .createTime(LocalDateTime.now())
                .build();
        transactionRepository.save(confirmTxn);

        // 复式记账：借-商户 贷-平台
        journalService.record(tccId, account.getId(), PLATFORM_ACCOUNT_ID,
                amount, "PAY", merchantId);

        log.info("[TCC-CONFIRM] 扣款成功: tccId={}, merchantId={}, amount={}, balanceAfter={}",
                tccId, merchantId, amount, account.getBalance());
    }

    @Override
    @Transactional
    public void cancel(CancelRequest request) {
        String tccId = request.getTccId();

        // 幂等：已 Cancel 过直接返回
        if (transactionRepository.findByTxnId(tccId + "_CANCEL").isPresent()) {
            log.info("[TCC-CANCEL] 幂等命中: tccId={}", tccId);
            return;
        }

        // 找到 Try 记录
        Transaction tryTxn = transactionRepository.findByTxnId(tccId)
                .orElseThrow(() -> new RuntimeException("TCC Try 记录不存在: " + tccId));

        Long merchantId = tryTxn.getMerchantId();
        BigDecimal amount = tryTxn.getAmount();

        // 释放冻结
        Account account = accountRepository.findByMerchantId(merchantId)
                .orElseThrow(() -> new EntityNotFoundException("账户不存在"));
        account.setFrozenAmount(account.getFrozenAmount().subtract(amount));
        accountRepository.save(account);

        // 记录 Cancel 交易（幂等键）
        Transaction cancelTxn = Transaction.builder()
                .id(IdUtil.getSnowflake(2, 1).nextId())
                .txnId(tccId + "_CANCEL")
                .merchantId(merchantId)
                .amount(amount)
                .txnType("UNFREEZE")
                .outTradeNo(tryTxn.getOutTradeNo())
                .status("SUCCESS")
                .createTime(LocalDateTime.now())
                .build();
        transactionRepository.save(cancelTxn);

        log.info("[TCC-CANCEL] 释放冻结: tccId={}, merchantId={}, amount={}",
                tccId, merchantId, amount);
    }
}
