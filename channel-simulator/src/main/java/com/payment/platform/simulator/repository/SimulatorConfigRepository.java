package com.payment.platform.simulator.repository;

import com.payment.platform.simulator.entity.SimulatorConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * 模拟器配置数据访问层。
 */
@Repository
public interface SimulatorConfigRepository extends JpaRepository<SimulatorConfig, Integer> {

    /**
     * 查询指定渠道的生效配置。
     * @param channelType 渠道类型（DEFAULT=默认兜底）
     * @param status      状态（ACTIVE）
     * @return 配置实体（可能为空）
     */
    Optional<SimulatorConfig> findByChannelTypeAndStatus(String channelType, String status);
}
