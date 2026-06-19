package com.payment.platform.simulator.config;

import com.payment.platform.simulator.entity.SimulatorConfig;
import com.payment.platform.simulator.repository.SimulatorConfigRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * 模拟器运行时配置管理器。
 * <p>从数据库读取配置并缓存（每次请求实时读取，保证动态生效）。
 * 如果数据库无配置则使用默认值兜底。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SimulatorConfigManager {

    private final SimulatorConfigRepository configRepository;

    /**
     * 获取指定渠道的当前配置（每次实时查询，确保动态调整能立即生效）。
     *
     * @param channelType 渠道类型（WECHAT / ALIPAY / UNIONPAY）
     * @return 模拟器配置
     */
    public SimulatorConfig getConfig(String channelType) {
        // 优先查指定渠道配置
        Optional<SimulatorConfig> configOpt = configRepository
                .findByChannelTypeAndStatus(channelType, "ACTIVE");
        if (configOpt.isPresent()) {
            return configOpt.get();
        }

        // 兜底：DEFAULT 配置
        configOpt = configRepository.findByChannelTypeAndStatus("DEFAULT", "ACTIVE");
        if (configOpt.isPresent()) {
            return configOpt.get();
        }

        // 最终兜底：硬编码默认值（首次启动数据库可能还没初始化）
        log.warn("[SIMULATOR] 未找到任何生效配置，使用硬编码默认值");
        return buildDefault();
    }

    /**
     * 硬编码默认配置：50ms 延迟，80% 成功，10% UNKNOWN。
     */
    private SimulatorConfig buildDefault() {
        return SimulatorConfig.builder()
                .channelType("DEFAULT")
                .delayMs(50)
                .successRate(new java.math.BigDecimal("0.80"))
                .unknownRate(new java.math.BigDecimal("0.10"))
                .status("ACTIVE")
                .build();
    }
}
