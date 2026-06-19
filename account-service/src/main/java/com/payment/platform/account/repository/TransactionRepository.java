package com.payment.platform.account.repository;

import com.payment.platform.account.entity.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * 交易记录数据访问层。
 */
@Repository
public interface TransactionRepository extends JpaRepository<Transaction, Long> {

    /**
     * 根据流水号查询（幂等键）。
     */
    Optional<Transaction> findByTxnId(String txnId);

    /**
     * 根据外部订单号查询（幂等去重）。
     */
    Optional<Transaction> findByOutTradeNoAndTxnType(String outTradeNo, String txnType);
}
