package com.payment.platform.simulator.repository;

import com.payment.platform.simulator.entity.ChannelOrder;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 渠道模拟器支付热路径的直接 JDBC 存储。
 */
@Repository
@RequiredArgsConstructor
public class DirectChannelOrderStore {

    private final JdbcTemplate jdbcTemplate;

    public boolean insert(ChannelOrder order) {
        return jdbcTemplate.update("""
                INSERT IGNORE INTO channel_order
                    (id,channel_order_no,out_trade_no,amount,status,channel_type,create_time)
                VALUES (?,?,?,?,?,?,?)
                """,
                order.getId(),
                order.getChannelOrderNo(),
                order.getOutTradeNo(),
                order.getAmount(),
                order.getStatus(),
                order.getChannelType(),
                Timestamp.valueOf(order.getCreateTime())) == 1;
    }

    public ChannelOrder findByOutTradeNo(String outTradeNo) {
        List<ChannelOrder> orders = jdbcTemplate.query("""
                SELECT id,channel_order_no,out_trade_no,amount,status,channel_type,create_time
                FROM channel_order
                WHERE out_trade_no=?
                LIMIT 1
                """, (result, rowNum) -> ChannelOrder.builder()
                .id(result.getLong("id"))
                .channelOrderNo(result.getString("channel_order_no"))
                .outTradeNo(result.getString("out_trade_no"))
                .amount(result.getBigDecimal("amount"))
                .status(result.getString("status"))
                .channelType(result.getString("channel_type"))
                .createTime(result.getTimestamp("create_time").toLocalDateTime())
                .build(), outTradeNo);
        return orders.isEmpty() ? null : orders.get(0);
    }
}
