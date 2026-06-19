package com.payment.platform.common.util;

import cn.hutool.core.util.IdUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 分布式 Snowflake ID 生成器。
 * <p>基于 Hutool 的 Snowflake 算法实现，本地生成无网络开销。
 * 64 位结构：1bit 保留 + 41bit 时间戳 + 5bit workerId + 5bit dataCenterId + 12bit 序列号。
 * 理论峰值 QPS 约 409 万/秒，完全满足项目需求。</p>
 *
 * <p>workerId 通过 Nacos 配置下发给每个服务实例，确保全局唯一：</p>
 * <ul>
 *   <li>payment-gateway: workerId = 1</li>
 *   <li>account-service: workerId = 2</li>
 *   <li>order-service: workerId = 3</li>
 *   <li>notification-service: workerId = 4</li>
 *   <li>reconciliation-service: workerId = 5</li>
 *   <li>merchant-service: workerId = 6</li>
 *   <li>channel-simulator: workerId = 7</li>
 * </ul>
 */
@Component
public class SnowflakeIdGenerator {

    private final cn.hutool.core.lang.Snowflake snowflake;

    /**
     * @param workerId 工作机器 ID（0-31），通过 Nacos 配置下发到每个实例
     * @param dataCenterId 数据中心 ID，单机房固定为 1
     */
    public SnowflakeIdGenerator(
            @Value("${snowflake.worker-id:1}") long workerId,
            @Value("${snowflake.data-center-id:1}") long dataCenterId) {
        this.snowflake = IdUtil.getSnowflake(workerId, dataCenterId);
    }

    /** @return 全局唯一 long 型 ID */
    public long nextId() {
        return snowflake.nextId();
    }

    /** @return 全局唯一字符串型 ID */
    public String nextIdStr() {
        return snowflake.nextIdStr();
    }
}
