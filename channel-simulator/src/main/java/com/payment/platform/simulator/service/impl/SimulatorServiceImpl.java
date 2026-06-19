package com.payment.platform.simulator.service.impl;

import cn.hutool.core.util.IdUtil;
import com.payment.platform.common.dto.request.ChannelPayRequest;
import com.payment.platform.common.dto.response.ChannelPayResponse;
import com.payment.platform.common.dto.response.ChannelQueryResponse;
import com.payment.platform.common.dto.event.BillDTO;
import com.payment.platform.simulator.entity.ChannelOrder;
import com.payment.platform.simulator.entity.SimulatorConfig;
import com.payment.platform.simulator.repository.ChannelOrderRepository;
import com.payment.platform.simulator.repository.SimulatorConfigRepository;
import com.payment.platform.simulator.service.SimulatorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

/**
 * 渠道模拟器服务实现。
 * <p>核心逻辑：接收支付请求 → 读取配置 → 按概率决定返回 SUCCESS/FAIL/UNKNOWN → 记录到 DB。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SimulatorServiceImpl implements SimulatorService {

    private final ChannelOrderRepository channelOrderRepository;
    private final SimulatorConfigRepository simulatorConfigRepository;

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    /**
     * 模拟发起支付。
     * <p>根据配置的成功率和 UNKNOWN 率随机决定返回什么。同时模拟配置的延迟时长。</p>
     */
    @Override
    @Transactional
    public ChannelPayResponse pay(ChannelPayRequest request) {
        // 读取当前生效的模拟器配置
        SimulatorConfig config = getActiveConfig(request.getChannelType());

        // 模拟网络延迟
        if (config.getDelayMs() > 0) {
            try {
                Thread.sleep(config.getDelayMs());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        // 生成渠道订单号
        String channelOrderNo = "CH" + System.currentTimeMillis()
                + IdUtil.fastSimpleUUID().substring(0, 6);

        // 按概率决定结果
        double roll = ThreadLocalRandom.current().nextDouble();
        String status;
        String message;

        // 生成 0~1 之间的随机数，与配置比较决定结果
        if (roll < config.getSuccessRate().doubleValue()) {
            status = "SUCCESS";
            message = "支付成功";
        } else if (roll < config.getSuccessRate().doubleValue()
                + config.getUnknownRate().doubleValue()) {
            status = "UNKNOWN";
            message = "支付处理中，结果未确定";
        } else {
            status = "FAIL";
            message = "支付失败，渠道拒绝";
        }

        // 记录到模拟渠道订单表
        ChannelOrder order = ChannelOrder.builder()
                .id(IdUtil.getSnowflake(1, 1).nextId())
                .channelOrderNo(channelOrderNo)
                .outTradeNo(request.getOutTradeNo())
                .amount(request.getAmount())
                .status(status)
                .channelType(request.getChannelType())
                .createTime(LocalDateTime.now())
                .build();
        channelOrderRepository.save(order);

        log.info("[SIMULATOR] 支付请求: outTradeNo={}, channelOrderNo={}, roll={}, status={}",
                request.getOutTradeNo(), channelOrderNo,
                String.format("%.2f", roll), status);

        return ChannelPayResponse.builder()
                .channelOrderNo(channelOrderNo)
                .outTradeNo(request.getOutTradeNo())
                .amount(request.getAmount())
                .status(status)
                .message(message)
                .build();
    }

    /**
     * 模拟查单。
     * <p>从 ChannelOrder 表查询支付请求的最终状态。</p>
     */
    @Override
    public ChannelQueryResponse query(String outTradeNo) {
        Optional<ChannelOrder> orderOpt = channelOrderRepository.findByOutTradeNo(outTradeNo);

        if (orderOpt.isEmpty()) {
            return ChannelQueryResponse.builder()
                    .outTradeNo(outTradeNo)
                    .status("NOT_FOUND")
                    .queryTime(LocalDateTime.now().toString())
                    .build();
        }

        ChannelOrder order = orderOpt.get();
        return ChannelQueryResponse.builder()
                .channelOrderNo(order.getChannelOrderNo())
                .outTradeNo(order.getOutTradeNo())
                .amount(order.getAmount())
                .status(order.getStatus())
                .queryTime(LocalDateTime.now().toString())
                .build();
    }

    /**
     * 获取指定日期的模拟账单。
     * <p>聚合指定日期范围内所有订单生成账单数据。</p>
     */
    @Override
    public List<BillDTO> getBill(String date) {
        LocalDate billDate = LocalDate.parse(date, DATE_FMT);
        LocalDateTime start = billDate.atStartOfDay();
        LocalDateTime end = billDate.plusDays(1).atStartOfDay();

        // 查询指定日期范围内的所有订单
        List<ChannelOrder> orders = channelOrderRepository.findByCreateTimeBetween(start, end);

        // 转换为账单 DTO
        return orders.stream()
                .map(order -> BillDTO.builder()
                        .channelOrderNo(order.getChannelOrderNo())
                        .outTradeNo(order.getOutTradeNo())
                        .amount(order.getAmount())
                        .status(order.getStatus())
                        .txnTime(order.getCreateTime().toString())
                        .channelType(order.getChannelType())
                        .build())
                .collect(Collectors.toList());
    }

    /**
     * 更新模拟器配置（动态生效）。
     */
    @Override
    @Transactional
    public SimulatorConfig updateConfig(SimulatorConfig config) {
        config.setUpdateTime(LocalDateTime.now());
        return simulatorConfigRepository.save(config);
    }

    /**
     * 获取当前生效的配置（如果没查到用默认值兜底）。
     */
    @Override
    public SimulatorConfig getCurrentConfig(String channelType) {
        return getActiveConfig(channelType);
    }

    /**
     * 获取生效的配置，如果指定渠道无配置则返回 DEFAULT 兜底配置。
     * <p>兜底配置值：成功率 80%，UNKNOWN 率 10%，延迟 50ms。</p>
     */
    private SimulatorConfig getActiveConfig(String channelType) {
        // 先查指定渠道的配置
        Optional<SimulatorConfig> configOpt = simulatorConfigRepository
                .findByChannelTypeAndStatus(channelType, "ACTIVE");

        if (configOpt.isPresent()) {
            return configOpt.get();
        }

        // 再查 DEFAULT 兜底
        Optional<SimulatorConfig> defaultOpt = simulatorConfigRepository
                .findByChannelTypeAndStatus("DEFAULT", "ACTIVE");

        if (defaultOpt.isPresent()) {
            return defaultOpt.get();
        }

        // 如果数据库都没配置（首次启动），返回硬编码的默认值
        return SimulatorConfig.builder()
                .delayMs(50)
                .successRate(new BigDecimal("0.80"))
                .unknownRate(new BigDecimal("0.10"))
                .build();
    }
}
