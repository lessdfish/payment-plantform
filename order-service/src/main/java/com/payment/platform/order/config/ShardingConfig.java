package com.payment.platform.order.config;

import org.apache.shardingsphere.driver.api.yaml.YamlShardingSphereDataSourceFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

/**
 * 订单服务 ShardingSphere 分片配置。
 * <p>4 个数据源，按 merchant_id 分片。订单表不按表进一步拆分（数据量小于账户流水）。</p>
 */
@Configuration
public class ShardingConfig {

    @Bean
    public DataSource shardingDataSource() throws Exception {
        String yaml = """
                dataSources:
                  ds0:
                    dataSourceClassName: com.zaxxer.hikari.HikariDataSource
                    driverClassName: com.mysql.cj.jdbc.Driver
                    jdbcUrl: jdbc:mysql://localhost:3306/payment_order_0?useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true
                    username: root
                    password: root123
                  ds1:
                    dataSourceClassName: com.zaxxer.hikari.HikariDataSource
                    driverClassName: com.mysql.cj.jdbc.Driver
                    jdbcUrl: jdbc:mysql://localhost:3307/payment_order_0?useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true
                    username: root
                    password: root123
                  ds2:
                    dataSourceClassName: com.zaxxer.hikari.HikariDataSource
                    driverClassName: com.mysql.cj.jdbc.Driver
                    jdbcUrl: jdbc:mysql://localhost:3308/payment_order_0?useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true
                    username: root
                    password: root123
                  ds3:
                    dataSourceClassName: com.zaxxer.hikari.HikariDataSource
                    driverClassName: com.mysql.cj.jdbc.Driver
                    jdbcUrl: jdbc:mysql://localhost:3309/payment_order_0?useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true
                    username: root
                    password: root123

                rules:
                - !SHARDING
                  tables:
                    order:
                      actualDataNodes: ds${0..3}.`order`
                      databaseStrategy:
                        standard:
                          shardingColumn: merchant_id
                          shardingAlgorithmName: order_db_inline
                    refund_order:
                      actualDataNodes: ds${0..3}.refund_order
                      databaseStrategy:
                        standard:
                          shardingColumn: merchant_id
                          shardingAlgorithmName: refund_db_inline

                  shardingAlgorithms:
                    order_db_inline:
                      type: INLINE
                      props:
                        algorithm-expression: ds${merchant_id % 4}
                    refund_db_inline:
                      type: INLINE
                      props:
                        algorithm-expression: ds${merchant_id % 4}

                props:
                  sql-show: false
                """;
        return YamlShardingSphereDataSourceFactory.createDataSource(
                yaml.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
}
