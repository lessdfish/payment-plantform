package com.payment.platform.gateway.service.impl;

import com.payment.platform.gateway.client.MerchantClient;
import com.payment.platform.gateway.dto.RouteResult;
import com.payment.platform.gateway.service.ChannelRouterService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 渠道路由服务实现。
 * <p>当前简化实现：按渠道优先级选择第一个可用渠道，默认走 WECHAT。
 * 生产环境可扩展为：多维度路由（费率最低 / 延迟最低 / 成功率最高 / 随机负载均衡）。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChannelRouterServiceImpl implements ChannelRouterService {

    private final MerchantClient merchantClient;

    /** 默认渠道优先级（从高到低） */
    private static final String[] DEFAULT_CHANNEL_ORDER = {"WECHAT", "ALIPAY", "UNIONPAY"};

    /**
     * 渠道路由选择。
     * <p>策略：遍历默认渠道列表，选择第一个商户已配置费率的渠道。</p>
     */
    @Override
    public RouteResult route(Long merchantId, BigDecimal amount) {
        // 遍历默认渠道优先级，选择第一个已配置费率的
        for (String channelType : DEFAULT_CHANNEL_ORDER) {
            BigDecimal feeRate = merchantClient.getFeeRate(merchantId, channelType);

            if (feeRate != null) {
                log.info("[ROUTE] 选择渠道: merchantId={}, channel={}, feeRate={}",
                        merchantId, channelType, feeRate);

                return RouteResult.builder()
                        .channelType(channelType)
                        .channelUrl("http://channel-simulator")
                        .feeRate(feeRate)
                        .build();
            }
        }

        // 如果商户未配置任何费率，默认走微信（费率 0）
        log.info("[ROUTE] 商户 {} 未配置任何费率，走默认渠道 WECHAT", merchantId);
        return RouteResult.builder()
                .channelType("WECHAT")
                .channelUrl("http://channel-simulator")
                .feeRate(BigDecimal.ZERO)
                .build();
    }
}
