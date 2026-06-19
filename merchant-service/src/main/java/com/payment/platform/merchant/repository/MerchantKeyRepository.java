package com.payment.platform.merchant.repository;

import com.payment.platform.merchant.entity.MerchantKey;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * 商户密钥数据访问层。
 */
@Repository
public interface MerchantKeyRepository extends JpaRepository<MerchantKey, Long> {

    /**
     * 查询商户当前生效的公钥。
     * @param merchantId 商户 ID
     * @param status     密钥状态（ACTIVE）
     * @return 密钥实体（可能为空）
     */
    Optional<MerchantKey> findByMerchantIdAndStatus(Long merchantId, String status);
}
