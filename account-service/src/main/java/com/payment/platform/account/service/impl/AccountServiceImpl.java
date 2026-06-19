package com.payment.platform.account.service.impl;

import cn.hutool.core.util.IdUtil;
import com.payment.platform.account.entity.Account;
import com.payment.platform.account.entity.Transaction;
import com.payment.platform.account.repository.AccountRepository;
import com.payment.platform.account.repository.TransactionRepository;
import com.payment.platform.account.service.AccountService;
import com.payment.platform.common.dto.response.AccountBalanceResponse;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 账户服务实现 — 余额查询、充值。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AccountServiceImpl implements AccountService {

    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;

    @Override
    public AccountBalanceResponse getBalance(Long merchantId) {
        Account account = accountRepository.findByMerchantId(merchantId)
                .orElseThrow(() -> new EntityNotFoundException("账户不存在: " + merchantId));

        return AccountBalanceResponse.builder()
                .accountId(account.getId())
                .balance(account.getBalance())
                .frozenAmount(account.getFrozenAmount())
                .availableBalance(account.getAvailableBalance())
                .build();
    }

    @Override
    @Transactional
    public void recharge(Long merchantId, BigDecimal amount, String outTradeNo) {
        // 幂等检查
        if (transactionRepository.findByOutTradeNoAndTxnType(outTradeNo, "RECHARGE").isPresent()) {
            log.info("[RECHARGE] 幂等命中: outTradeNo={}", outTradeNo);
            return;
        }

        // 获取或创建账户
        Account account = accountRepository.findByMerchantId(merchantId)
                .orElseGet(() -> {
                    Account newAccount = Account.builder()
                            .id(IdUtil.getSnowflake(2, 1).nextId())
                            .merchantId(merchantId)
                            .balance(BigDecimal.ZERO)
                            .frozenAmount(BigDecimal.ZERO)
                            .version(0)
                            .createTime(LocalDateTime.now())
                            .updateTime(LocalDateTime.now())
                            .build();
                    log.info("[ACCOUNT] 创建新账户: merchantId={}", merchantId);
                    return accountRepository.save(newAccount);
                });

        // 增加余额
        account.setBalance(account.getBalance().add(amount));
        accountRepository.save(account);

        // 记录交易
        Transaction txn = Transaction.builder()
                .id(IdUtil.getSnowflake(2, 1).nextId())
                .txnId("RCH" + System.currentTimeMillis())
                .merchantId(merchantId)
                .amount(amount)
                .txnType("RECHARGE")
                .outTradeNo(outTradeNo)
                .status("SUCCESS")
                .createTime(LocalDateTime.now())
                .build();
        transactionRepository.save(txn);

        log.info("[RECHARGE] 充值成功: merchantId={}, amount={}, balanceAfter={}",
                merchantId, amount, account.getBalance());
    }
}
