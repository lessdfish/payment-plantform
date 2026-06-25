package com.payment.platform.account.repository;

import cn.hutool.core.util.IdUtil;
import com.payment.platform.common.dto.request.TryRequest;
import com.payment.platform.common.dto.response.TryResponse;
import com.payment.platform.common.exception.BalanceInsufficientException;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * TCC 热路径的直接分片实现。
 * <p>一次请求只访问 merchantId 对应的单个库表，避免 ORM merge 和
 * 缺少分片键时的全分片广播。</p>
 */
@Repository
@RequiredArgsConstructor
public class DirectTccLedgerRepository {

    private static final long PLATFORM_ACCOUNT_ID = 0L;
    private static final AtomicInteger TCC_SEQUENCE = new AtomicInteger();

    private final LedgerShardConnectionProvider shards;

    public TryResponse tryFreeze(TryRequest request) {
        long merchantId = request.getMerchantId();
        BigDecimal amount = request.getAmount();
        String tccId = createTccId(merchantId);

        try (Connection connection = shards.getConnection(merchantId)) {
            connection.setAutoCommit(false);
            try {
                int updated = insertFreezeAndUpdate(
                        connection, merchantId, tccId, amount,
                        request.getBizOrderNo());
                if (updated == 0) {
                    BigDecimal available = findAvailableBalance(connection, merchantId);
                    connection.rollback();
                    if (available == null) {
                        throw new EntityNotFoundException("账户不存在: " + merchantId);
                    }
                    throw new BalanceInsufficientException(available, amount);
                }

                connection.commit();
                return TryResponse.builder()
                        .tccId(tccId)
                        .frozenAmount(amount)
                        .build();
            } catch (SQLException e) {
                rollback(connection);
                if (isConstraintViolation(e)) {
                    TryTransaction existing = findByTradeNo(
                            connection, merchantId, request.getBizOrderNo(), "FREEZE");
                    if (existing != null) {
                        return TryResponse.builder()
                                .tccId(existing.txnId())
                                .frozenAmount(existing.amount())
                                .build();
                    }
                }
                throw databaseFailure("TCC Try 执行失败", e);
            } catch (RuntimeException e) {
                rollback(connection);
                throw e;
            }
        } catch (SQLException e) {
            throw databaseFailure("获取账务分片连接失败", e);
        }
    }

    public ConfirmedPayment confirm(String tccId) {
        Long embeddedMerchantId = extractMerchantId(tccId);
        long merchantId = embeddedMerchantId != null
                ? embeddedMerchantId
                : resolveTryTransaction(tccId).merchantId();

        try (Connection connection = shards.getConnection(merchantId)) {
            connection.setAutoCommit(false);
            try {
                ConfirmContext context =
                        findConfirmContext(connection, merchantId, tccId);
                if (context == null) {
                    throw new EntityNotFoundException("TCC Try 记录不存在: " + tccId);
                }

                int updated = insertConfirmDebitAndJournal(
                        connection, merchantId, tccId, context);
                if (updated == 0) {
                    throw new IllegalStateException("账户冻结金额不足: " + tccId);
                }
                connection.commit();
                return new ConfirmedPayment(tccId, merchantId, context.accountId(),
                        context.amount(), context.outTradeNo());
            } catch (SQLException e) {
                rollback(connection);
                if (isConstraintViolation(e)
                        && markerExists(merchantId, tccId + "_CONFIRM")) {
                    return null;
                }
                throw databaseFailure("TCC Confirm 执行失败", e);
            } catch (RuntimeException e) {
                rollback(connection);
                throw e;
            }
        } catch (SQLException e) {
            throw databaseFailure("获取账务分片连接失败", e);
        }
    }

    public void cancel(String tccId) {
        TryTransaction initial = resolveTryTransaction(tccId);
        long merchantId = initial.merchantId();

        try (Connection connection = shards.getConnection(merchantId)) {
            connection.setAutoCommit(false);
            try {
                TryTransaction tryTxn = findByTxnId(connection, merchantId, tccId);
                if (tryTxn == null) {
                    throw new EntityNotFoundException("TCC Try 记录不存在: " + tccId);
                }
                if (findByTxnId(connection, merchantId, tccId + "_CANCEL") != null) {
                    connection.commit();
                    return;
                }
                if (findByTxnId(connection, merchantId, tccId + "_CONFIRM") != null) {
                    throw new IllegalStateException("TCC 已确认，不能取消: " + tccId);
                }
                if (releaseFrozen(connection, merchantId, tryTxn.amount()) == 0) {
                    throw new IllegalStateException("账户冻结金额不足: " + tccId);
                }
                insertTransaction(connection, merchantId,
                        IdUtil.getSnowflake(2, 1).nextId(),
                        tccId + "_CANCEL", tryTxn.amount(), "UNFREEZE",
                        tryTxn.outTradeNo(), "SUCCESS");
                connection.commit();
            } catch (SQLException e) {
                rollback(connection);
                if (isConstraintViolation(e)
                        && markerExists(merchantId, tccId + "_CANCEL")) {
                    return;
                }
                throw databaseFailure("TCC Cancel 执行失败", e);
            } catch (RuntimeException e) {
                rollback(connection);
                throw e;
            }
        } catch (SQLException e) {
            throw databaseFailure("获取账务分片连接失败", e);
        }
    }

    private TryTransaction resolveTryTransaction(String tccId) {
        Long merchantId = extractMerchantId(tccId);
        if (merchantId != null) {
            try (Connection connection = shards.getConnection(merchantId)) {
                TryTransaction transaction = findByTxnId(connection, merchantId, tccId);
                if (transaction != null) {
                    return transaction;
                }
            } catch (SQLException e) {
                throw databaseFailure("查询 TCC Try 记录失败", e);
            }
            throw new EntityNotFoundException("TCC Try 记录不存在: " + tccId);
        }

        for (int database = 0; database < 4; database++) {
            try (Connection connection = shards.getConnectionByDatabaseShard(database)) {
                for (int table = 0; table < 8; table++) {
                    TryTransaction transaction =
                            findByTxnIdInTable(connection, "transaction_" + table, tccId);
                    if (transaction != null) {
                        return transaction;
                    }
                }
            } catch (SQLException e) {
                throw databaseFailure("查询旧版 TCC Try 记录失败", e);
            }
        }
        throw new EntityNotFoundException("TCC Try 记录不存在: " + tccId);
    }

    private TryTransaction findByTradeNo(Connection connection, long merchantId,
                                         String outTradeNo, String txnType)
            throws SQLException {
        String sql = "SELECT txn_id, merchant_id, amount, out_trade_no "
                + "FROM `" + shards.transactionTable(merchantId) + "` "
                + "WHERE merchant_id=? AND out_trade_no=? AND txn_type=? LIMIT 1";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, merchantId);
            statement.setString(2, outTradeNo);
            statement.setString(3, txnType);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? mapTryTransaction(result) : null;
            }
        }
    }

    private TryTransaction findByTxnId(Connection connection, long merchantId,
                                       String txnId) throws SQLException {
        return findByTxnIdInTable(
                connection, shards.transactionTable(merchantId), txnId);
    }

    private TryTransaction findByTxnIdInTable(Connection connection, String table,
                                              String txnId) throws SQLException {
        String sql = "SELECT txn_id, merchant_id, amount, out_trade_no "
                + "FROM `" + table + "` WHERE txn_id=? LIMIT 1";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, txnId);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? mapTryTransaction(result) : null;
            }
        }
    }

    private ConfirmContext findConfirmContext(Connection connection, long merchantId,
                                              String txnId) throws SQLException {
        String sql = "SELECT t.amount,t.out_trade_no,a.id AS account_id "
                + "FROM `" + shards.transactionTable(merchantId) + "` t "
                + "JOIN `" + shards.accountTable(merchantId) + "` a "
                + "ON a.merchant_id=t.merchant_id "
                + "WHERE t.txn_id=? AND t.merchant_id=? LIMIT 1";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, txnId);
            statement.setLong(2, merchantId);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    return null;
                }
                return new ConfirmContext(
                        result.getLong("account_id"),
                        result.getBigDecimal("amount"),
                        result.getString("out_trade_no"));
            }
        }
    }

    private TryTransaction mapTryTransaction(ResultSet result) throws SQLException {
        return new TryTransaction(
                result.getString("txn_id"),
                result.getLong("merchant_id"),
                result.getBigDecimal("amount"),
                result.getString("out_trade_no"));
    }

    private int insertFreezeAndUpdate(Connection connection, long merchantId,
                                      String tccId, BigDecimal amount,
                                      String outTradeNo) throws SQLException {
        String sql = "INSERT INTO `" + shards.transactionTable(merchantId) + "` "
                + "(id,txn_id,merchant_id,amount,txn_type,out_trade_no,status,create_time) "
                + "VALUES (?,?,?,?,?,?,?,?);"
                + "UPDATE `" + shards.accountTable(merchantId) + "` "
                + "SET frozen_amount=frozen_amount+?, update_time=NOW() "
                + "WHERE merchant_id=? AND balance-frozen_amount>=?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, IdUtil.getSnowflake(2, 1).nextId());
            statement.setString(2, tccId);
            statement.setLong(3, merchantId);
            statement.setBigDecimal(4, amount);
            statement.setString(5, "FREEZE");
            statement.setString(6, outTradeNo);
            statement.setString(7, "FROZEN");
            statement.setTimestamp(8, Timestamp.valueOf(LocalDateTime.now()));
            statement.setBigDecimal(9, amount);
            statement.setLong(10, merchantId);
            statement.setBigDecimal(11, amount);
            int[] counts = executeUpdateCounts(statement, 2);
            return counts[1];
        }
    }

    private int insertConfirmDebitAndJournal(Connection connection, long merchantId,
                                             String tccId, ConfirmContext context)
            throws SQLException {
        String transactionTable = shards.transactionTable(merchantId);
        String accountTable = shards.accountTable(merchantId);
        String journalTable = shards.journalTable(merchantId);
        String sql = "INSERT INTO `" + transactionTable + "` "
                + "(id,txn_id,merchant_id,amount,txn_type,out_trade_no,status,create_time) "
                + "VALUES (?,?,?,?,?,?,?,?);"
                + "UPDATE `" + accountTable + "` "
                + "SET balance=balance-?, frozen_amount=frozen_amount-?, update_time=NOW() "
                + "WHERE merchant_id=? AND frozen_amount>=?;"
                + "INSERT INTO `" + journalTable + "` "
                + "(id,txn_id,debit_account_id,credit_account_id,amount,"
                + "dr_cr_flag,txn_type,txn_time,merchant_id,out_trade_no) "
                + "VALUES (?,?,?,?,?,?,?,?,?,?),(?,?,?,?,?,?,?,?,?,?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            Timestamp now = Timestamp.valueOf(LocalDateTime.now());
            statement.setLong(1, IdUtil.getSnowflake(2, 1).nextId());
            statement.setString(2, tccId + "_CONFIRM");
            statement.setLong(3, merchantId);
            statement.setBigDecimal(4, context.amount());
            statement.setString(5, "PAY");
            statement.setString(6, context.outTradeNo());
            statement.setString(7, "SUCCESS");
            statement.setTimestamp(8, now);
            statement.setBigDecimal(9, context.amount());
            statement.setBigDecimal(10, context.amount());
            statement.setLong(11, merchantId);
            statement.setBigDecimal(12, context.amount());
            setJournalValues(statement, 13,
                    IdUtil.getSnowflake(2, 1).nextId(), tccId,
                    context.accountId(), context.amount(), "D",
                    now, merchantId, context.outTradeNo());
            setJournalValues(statement, 23,
                    IdUtil.getSnowflake(2, 1).nextId(), tccId,
                    context.accountId(), context.amount(), "C",
                    now, merchantId, context.outTradeNo());
            int[] counts = executeUpdateCounts(statement, 3);
            return counts[1];
        }
    }

    private void setJournalValues(PreparedStatement statement, int offset,
                                  long id, String txnId, long debitAccountId,
                                  BigDecimal amount, String flag, Timestamp timestamp,
                                  long merchantId, String outTradeNo)
            throws SQLException {
        statement.setLong(offset, id);
        statement.setString(offset + 1, txnId);
        statement.setLong(offset + 2, debitAccountId);
        statement.setLong(offset + 3, PLATFORM_ACCOUNT_ID);
        statement.setBigDecimal(offset + 4, amount);
        statement.setString(offset + 5, flag);
        statement.setString(offset + 6, "PAY");
        statement.setTimestamp(offset + 7, timestamp);
        statement.setLong(offset + 8, merchantId);
        statement.setString(offset + 9, outTradeNo);
    }

    private int[] executeUpdateCounts(PreparedStatement statement,
                                      int expectedStatements)
            throws SQLException {
        int[] counts = new int[expectedStatements];
        boolean resultSet = statement.execute();
        for (int i = 0; i < expectedStatements; i++) {
            if (resultSet) {
                throw new SQLException("账务热路径不应返回结果集");
            }
            counts[i] = statement.getUpdateCount();
            if (i + 1 < expectedStatements) {
                resultSet = statement.getMoreResults();
            }
        }
        return counts;
    }

    private int releaseFrozen(Connection connection, long merchantId,
                              BigDecimal amount) throws SQLException {
        String sql = "UPDATE `" + shards.accountTable(merchantId) + "` "
                + "SET frozen_amount=frozen_amount-?, update_time=NOW() "
                + "WHERE merchant_id=? AND frozen_amount>=?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setBigDecimal(1, amount);
            statement.setLong(2, merchantId);
            statement.setBigDecimal(3, amount);
            return statement.executeUpdate();
        }
    }

    private BigDecimal findAvailableBalance(Connection connection, long merchantId)
            throws SQLException {
        AccountSnapshot account = findAccount(connection, merchantId);
        return account == null ? null : account.availableBalance();
    }

    private AccountSnapshot findAccount(Connection connection, long merchantId)
            throws SQLException {
        String sql = "SELECT id, balance, frozen_amount "
                + "FROM `" + shards.accountTable(merchantId) + "` "
                + "WHERE merchant_id=? LIMIT 1";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, merchantId);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    return null;
                }
                BigDecimal balance = result.getBigDecimal("balance");
                BigDecimal frozen = result.getBigDecimal("frozen_amount");
                return new AccountSnapshot(
                        result.getLong("id"), balance, balance.subtract(frozen));
            }
        }
    }

    private void insertTransaction(Connection connection, long merchantId, long id,
                                   String txnId, BigDecimal amount, String txnType,
                                   String outTradeNo, String status)
            throws SQLException {
        String sql = "INSERT INTO `" + shards.transactionTable(merchantId) + "` "
                + "(id,txn_id,merchant_id,amount,txn_type,out_trade_no,status,create_time) "
                + "VALUES (?,?,?,?,?,?,?,?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, id);
            statement.setString(2, txnId);
            statement.setLong(3, merchantId);
            statement.setBigDecimal(4, amount);
            statement.setString(5, txnType);
            statement.setString(6, outTradeNo);
            statement.setString(7, status);
            statement.setTimestamp(8, Timestamp.valueOf(LocalDateTime.now()));
            statement.executeUpdate();
        }
    }

    private boolean markerExists(long merchantId, String marker) {
        try (Connection connection = shards.getConnection(merchantId)) {
            return findByTxnId(connection, merchantId, marker) != null;
        } catch (SQLException e) {
            throw databaseFailure("查询 TCC 幂等标记失败", e);
        }
    }

    private String createTccId(long merchantId) {
        long compactTime = System.currentTimeMillis() % 78_364_164_096L;
        String sequence = Integer.toString(
                Math.floorMod(TCC_SEQUENCE.getAndIncrement(), 1_296), 36);
        if (sequence.length() == 1) {
            sequence = "0" + sequence;
        }
        return "T" + Long.toUnsignedString(merchantId, 36)
                + "_" + Long.toUnsignedString(compactTime, 36)
                + sequence;
    }

    private Long extractMerchantId(String tccId) {
        int separator = tccId.indexOf('_');
        if (tccId.startsWith("T") && !tccId.startsWith("TCC") && separator > 1) {
            try {
                return Long.parseUnsignedLong(tccId.substring(1, separator), 36);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private boolean isConstraintViolation(SQLException exception) {
        return exception.getSQLState() != null
                && exception.getSQLState().startsWith("23");
    }

    private void rollback(Connection connection) {
        try {
            connection.rollback();
        } catch (SQLException ignored) {
            // 原异常更有诊断价值。
        }
    }

    private RuntimeException databaseFailure(String message, SQLException cause) {
        return new IllegalStateException(message, cause);
    }

    private record TryTransaction(String txnId, long merchantId,
                                  BigDecimal amount, String outTradeNo) {
    }

    private record AccountSnapshot(long accountId, BigDecimal balance,
                                   BigDecimal availableBalance) {
    }

    private record ConfirmContext(long accountId, BigDecimal amount,
                                  String outTradeNo) {
    }

    public record ConfirmedPayment(String txnId, long merchantId, long accountId,
                                   BigDecimal amount, String outTradeNo) {
    }
}
