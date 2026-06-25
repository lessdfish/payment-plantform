package com.payment.platform.account.service.impl;

import com.payment.platform.account.repository.DirectTccLedgerRepository;
import com.payment.platform.account.repository.DirectTccLedgerRepository.ConfirmedPayment;
import com.payment.platform.account.service.TccService;
import com.payment.platform.common.dto.request.CancelRequest;
import com.payment.platform.common.dto.request.ConfirmRequest;
import com.payment.platform.common.dto.request.TryRequest;
import com.payment.platform.common.dto.response.TryResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * TCC 分布式事务服务。
 * <p>Try/Confirm/Cancel 均由直接分片仓储在单库事务中完成，
 * 避免热路径发生全分片广播。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TccServiceImpl implements TccService {

    private final DirectTccLedgerRepository ledgerRepository;

    @Override
    public TryResponse tryFreeze(TryRequest request) {
        TryResponse response = ledgerRepository.tryFreeze(request);
        log.info("[TCC-TRY] 冻结成功: tccId={}, merchantId={}, amount={}",
                response.getTccId(), request.getMerchantId(), request.getAmount());
        return response;
    }

    @Override
    public TryResponse executePayment(TryRequest request) {
        TryResponse response = tryFreeze(request);
        ConfirmRequest confirmRequest = new ConfirmRequest();
        confirmRequest.setTccId(response.getTccId());
        try {
            confirm(confirmRequest);
            return response;
        } catch (RuntimeException confirmFailure) {
            CancelRequest cancelRequest = new CancelRequest();
            cancelRequest.setTccId(response.getTccId());
            try {
                cancel(cancelRequest);
            } catch (RuntimeException cancelFailure) {
                confirmFailure.addSuppressed(cancelFailure);
            }
            throw confirmFailure;
        }
    }

    @Override
    public void confirm(ConfirmRequest request) {
        ConfirmedPayment confirmed = ledgerRepository.confirm(request.getTccId());
        if (confirmed == null) {
            log.info("[TCC-CONFIRM] 幂等命中: tccId={}", request.getTccId());
            return;
        }

        log.info("[TCC-CONFIRM] 扣款成功: tccId={}, merchantId={}, amount={}",
                confirmed.txnId(), confirmed.merchantId(),
                confirmed.amount());
    }

    @Override
    public void cancel(CancelRequest request) {
        ledgerRepository.cancel(request.getTccId());
        log.info("[TCC-CANCEL] 释放冻结: tccId={}", request.getTccId());
    }
}
