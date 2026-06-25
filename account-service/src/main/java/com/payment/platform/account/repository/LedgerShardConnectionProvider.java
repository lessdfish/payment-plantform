package com.payment.platform.account.repository;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * 账务热路径的物理分片连接提供器。
 * <p>分片规则与 ShardingSphere 配置保持一致：库 merchantId % 4，
 * 表 merchantId % 8。</p>
 */
@Component
public class LedgerShardConnectionProvider {

    private static final int DATABASE_SHARDS = 4;
    private static final int TABLE_SHARDS = 8;
    private static final int[] PORTS = {13306, 3307, 3308, 3309};

    private final HikariDataSource[] dataSources = new HikariDataSource[DATABASE_SHARDS];

    public LedgerShardConnectionProvider() {
        for (int i = 0; i < DATABASE_SHARDS; i++) {
            HikariConfig config = new HikariConfig();
            config.setPoolName("ledger-hot-ds" + i);
            config.setDriverClassName("com.mysql.cj.jdbc.Driver");
            config.setJdbcUrl("jdbc:mysql://localhost:" + PORTS[i]
                    + "/payment_account_0?useSSL=false"
                    + "&serverTimezone=Asia/Shanghai"
                    + "&allowPublicKeyRetrieval=true"
                    + "&cachePrepStmts=true"
                    + "&useServerPrepStmts=true"
                    + "&prepStmtCacheSize=250"
                    + "&prepStmtCacheSqlLimit=2048"
                    + "&allowMultiQueries=true");
            config.setUsername("root");
            config.setPassword("root123");
            config.setMaximumPoolSize(50);
            config.setMinimumIdle(5);
            config.setConnectionTimeout(5_000);
            config.setTransactionIsolation("TRANSACTION_READ_COMMITTED");
            dataSources[i] = new HikariDataSource(config);
        }
    }

    public Connection getConnection(long merchantId) throws SQLException {
        return dataSources[databaseShard(merchantId)].getConnection();
    }

    public Connection getConnectionByDatabaseShard(int shard) throws SQLException {
        return dataSources[shard].getConnection();
    }

    public int databaseShard(long merchantId) {
        return Math.floorMod(merchantId, DATABASE_SHARDS);
    }

    public int tableShard(long merchantId) {
        return Math.floorMod(merchantId, TABLE_SHARDS);
    }

    public String accountTable(long merchantId) {
        return "account_" + tableShard(merchantId);
    }

    public String transactionTable(long merchantId) {
        return "transaction_" + tableShard(merchantId);
    }

    public String journalTable(long merchantId) {
        return "journal_entry_" + tableShard(merchantId);
    }

    @PreDestroy
    public void close() {
        for (HikariDataSource dataSource : dataSources) {
            if (dataSource != null) {
                dataSource.close();
            }
        }
    }
}
