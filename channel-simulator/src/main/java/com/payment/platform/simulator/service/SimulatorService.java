package com.payment.platform.simulator.service;

import com.payment.platform.common.dto.request.ChannelPayRequest;
import com.payment.platform.common.dto.response.ChannelPayResponse;
import com.payment.platform.common.dto.response.ChannelQueryResponse;
import com.payment.platform.common.dto.event.BillDTO;
import com.payment.platform.simulator.entity.SimulatorConfig;

import java.util.List;

/**
 * 渠道模拟器服务接口。
 */
public interface SimulatorService {

    /**
     * 模拟发起支付。
     * <p>根据配置的概率随机返回 SUCCESS / FAIL / UNKNOWN。</p>
     *
     * @param request 支付请求
     * @return 支付响应（含渠道订单号和结果状态）
     */
    ChannelPayResponse pay(ChannelPayRequest request);

    /**
     * 模拟查单。
     * <p>根据商户订单号查询之前发起的支付结果，用于 UNKNOWN 状态的二次确认。</p>
     *
     * @param outTradeNo 商户订单号
     * @return 查单结果
     */
    ChannelQueryResponse query(String outTradeNo);

    /**
     * 获取指定日期的模拟账单。
     * <p>从 ChannelOrder 表中聚合指定日期范围内的所有订单。</p>
     *
     * @param date 账单日期（yyyy-MM-dd）
     * @return 账单行列表
     */
    List<BillDTO> getBill(String date);

    /**
     * 更新模拟器配置（动态生效）。
     */
    SimulatorConfig updateConfig(SimulatorConfig config);

    /**
     * 获取当前生效的模拟器配置。
     */
    SimulatorConfig getCurrentConfig(String channelType);
}
