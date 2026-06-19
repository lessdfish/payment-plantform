package com.payment.platform.account.repository;

import com.payment.platform.account.entity.JournalEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 复式记账流水数据访问层。
 */
@Repository
public interface JournalEntryRepository extends JpaRepository<JournalEntry, Long> {

    /**
     * 根据流水号查询借贷双方记录（用于对账校验借贷平衡）。
     */
    List<JournalEntry> findByTxnId(String txnId);
}
