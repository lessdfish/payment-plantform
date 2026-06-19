package com.payment.platform.account.repository;

import com.payment.platform.account.entity.Account;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * 账户数据访问层。
 */
@Repository
public interface AccountRepository extends JpaRepository<Account, Long> {

    /**
     * 根据商户 ID 查询（分片键精准路由）。
     */
    Optional<Account> findByMerchantId(Long merchantId);

    /**
     * 带悲观锁查询，用于充值等需要排他锁的操作。
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM Account a WHERE a.merchantId = :merchantId")
    Optional<Account> findByMerchantIdForUpdate(Long merchantId);
}
