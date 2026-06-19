package com.payment.platform.account.config;

import org.apache.shardingsphere.driver.api.yaml.YamlShardingSphereDataSourceFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

/**
 * ShardingSphere 分库分表配置（YAML 方式）。
 * <p>4 库 × 8 表 = 32 个分片。分库键和分表键均为 merchant_id。</p>
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
                    jdbcUrl: jdbc:mysql://localhost:3306/payment_account_0?useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true
                    username: root
                    password: root123
                  ds1:
                    dataSourceClassName: com.zaxxer.hikari.HikariDataSource
                    driverClassName: com.mysql.cj.jdbc.Driver
                    jdbcUrl: jdbc:mysql://localhost:3307/payment_account_0?useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true
                    username: root
                    password: root123
                  ds2:
                    dataSourceClassName: com.zaxxer.hikari.HikariDataSource
                    driverClassName: com.mysql.cj.jdbc.Driver
                    jdbcUrl: jdbc:mysql://localhost:3308/payment_account_0?useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true
                    username: root
                    password: root123
                  ds3:
                    dataSourceClassName: com.zaxxer.hikari.HikariDataSource
                    driverClassName: com.mysql.cj.jdbc.Driver
                    jdbcUrl: jdbc:mysql://localhost:3309/payment_account_0?useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true
                    username: root
                    password: root123

                rules:
                - !SHARDING
                  tables:
                    account:
                      actualDataNodes: ds${0..3}.account_${0..7}
                      databaseStrategy:
                        standard:
                          shardingColumn: merchant_id
                          shardingAlgorithmName: account_db_inline
                      tableStrategy:
                        standard:
                          shardingColumn: merchant_id
                          shardingAlgorithmName: account_table_inline
                    transaction:
                      actualDataNodes: ds${0..3}.transaction_${0..7}
                      databaseStrategy:
                        standard:
                          shardingColumn: merchant_id
                          shardingAlgorithmName: txn_db_inline
                      tableStrategy:
                        standard:
                          shardingColumn: merchant_id
                          shardingAlgorithmName: txn_table_inline
                    journal_entry:
                      actualDataNodes: ds${0..3}.journal_entry_${0..7}
                      databaseStrategy:
                        standard:
                          shardingColumn: merchant_id
                          shardingAlgorithmName: je_db_inline
                      tableStrategy:
                        standard:
                          shardingColumn: merchant_id
                          shardingAlgorithmName: je_table_inline

                  shardingAlgorithms:
                    account_db_inline:
                      type: INLINE
                      props:
                        algorithm-expression: ds${merchant_id % 4}
                    account_table_inline:
                      type: INLINE
                      props:
                        algorithm-expression: account_${merchant_id % 8}
                    txn_db_inline:
                      type: INLINE
                      props:
                        algorithm-expression: ds${merchant_id % 4}
                    txn_table_inline:
                      type: INLINE
                      props:
                        algorithm-expression: transaction_${merchant_id % 8}
                    je_db_inline:
                      type: INLINE
                      props:
                        algorithm-expression: ds${merchant_id % 4}
                    je_table_inline:
                      type: INLINE
                      props:
                        algorithm-expression: journal_entry_${merchant_id % 8}

                props:
                  sql-show: false
                """;

        return YamlShardingSphereDataSourceFactory.createDataSource(
                yaml.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
}
