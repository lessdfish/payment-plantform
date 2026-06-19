package com.payment.platform.merchant.repository;

import com.payment.platform.merchant.entity.Merchant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * 商户数据访问层。
 */
@Repository
public interface MerchantRepository extends JpaRepository<Merchant, Long> {

    /**
     * 根据商户编号查询。
     * @param merchantNo 商户编号
     * @return 商户实体（可能为空）
     */
    Optional<Merchant> findByMerchantNo(String merchantNo);

    /**
     * 根据 API 密钥查询。
     * @param apiKey API 密钥
     * @return 商户实体（可能为空）
     */
    Optional<Merchant> findByApiKey(String apiKey);

    /**
     * 检查商户编号是否已存在（用于注册时去重）。
     */
    boolean existsByMerchantNo(String merchantNo);
}
