package com.payment.platform.simulator.repository;

import com.payment.platform.simulator.entity.ChannelOrder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 模拟渠道订单数据访问层。
 */
@Repository
public interface ChannelOrderRepository extends JpaRepository<ChannelOrder, Long> {

    /**
     * 根据渠道订单号查询。
     */
    Optional<ChannelOrder> findByChannelOrderNo(String channelOrderNo);

    /**
     * 根据商户订单号查询（用于查单接口）。
     */
    Optional<ChannelOrder> findByOutTradeNo(String outTradeNo);

    /**
     * 按时间范围查询所有完成的订单（用于账单生成）。
     * @param start 开始时间（含）
     * @param end   结束时间（不含）
     * @return 指定时间范围内的订单列表
     */
    List<ChannelOrder> findByCreateTimeBetween(LocalDateTime start, LocalDateTime end);
}
