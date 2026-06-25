package com.payment.platform.order.repository;

import cn.hutool.core.util.IdUtil;
import com.payment.platform.common.dto.event.PaySuccessEvent;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Repository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;

/**
 * 支付成功事件的订单热路径写入器。
 */
@Repository
public class DirectSettledOrderWriter {

    private static final int[] PORTS = {13306, 3307, 3308, 3309};

    private final HikariDataSource[] dataSources = new HikariDataSource[4];

    public DirectSettledOrderWriter() {
        for (int i = 0; i < dataSources.length; i++) {
            HikariConfig config = new HikariConfig();
            config.setPoolName("order-hot-ds" + i);
            config.setDriverClassName("com.mysql.cj.jdbc.Driver");
            config.setJdbcUrl("jdbc:mysql://localhost:" + PORTS[i]
                    + "/payment_order_0?useSSL=false"
                    + "&serverTimezone=Asia/Shanghai"
                    + "&allowPublicKeyRetrieval=true"
                    + "&cachePrepStmts=true"
                    + "&useServerPrepStmts=true");
            config.setUsername("root");
            config.setPassword("root123");
            config.setMaximumPoolSize(30);
            config.setMinimumIdle(3);
            config.setConnectionTimeout(5_000);
            dataSources[i] = new HikariDataSource(config);
        }
    }

    public void write(PaySuccessEvent event) {
        int shard = Math.floorMod(event.getMerchantId(), dataSources.length);
        String sql = """
                INSERT INTO payment_order
                    (id,order_no,out_trade_no,merchant_id,amount,status,
                     channel_order_no,notify_url,create_time,update_time)
                VALUES (?,?,?,?,?,'SETTLED',?,?,?,?)
                ON DUPLICATE KEY UPDATE
                    status='SETTLED',
                    channel_order_no=VALUES(channel_order_no),
                    notify_url=VALUES(notify_url),
                    update_time=VALUES(update_time)
                """;
        LocalDateTime now = LocalDateTime.now();
        try (Connection connection = dataSources[shard].getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, IdUtil.getSnowflake(3, 1).nextId());
            statement.setString(2, "ORD" + System.currentTimeMillis()
                    + IdUtil.fastSimpleUUID().substring(0, 4));
            statement.setString(3, event.getOutTradeNo());
            statement.setLong(4, event.getMerchantId());
            statement.setBigDecimal(5, event.getAmount());
            statement.setString(6, event.getChannelOrderNo());
            statement.setString(7, event.getNotifyUrl());
            statement.setTimestamp(8, Timestamp.valueOf(now));
            statement.setTimestamp(9, Timestamp.valueOf(now));
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException(
                    "支付成功订单写入失败: " + event.getOutTradeNo(), e);
        }
    }

    @PreDestroy
    public void close() {
        for (HikariDataSource dataSource : dataSources) {
            dataSource.close();
        }
    }
}
