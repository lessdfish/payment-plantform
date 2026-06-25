package com.payment.platform.notification.repository;

import com.payment.platform.notification.entity.NotifyRecord;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 通知消费热路径的直接 JDBC 存储。
 */
@Repository
@RequiredArgsConstructor
public class DirectNotifyStore {

    private final JdbcTemplate jdbcTemplate;

    public NotifyRecord createIfAbsent(String outTradeNo, Long merchantId,
                                       String notifyUrl, String body) {
        jdbcTemplate.update("""
                INSERT IGNORE INTO notify_record
                    (merchant_id,out_trade_no,notify_url,body,status,retry_count,create_time)
                VALUES (?,?,?,?, 'PENDING', 0, NOW(6))
                """, merchantId, outTradeNo, notifyUrl, body);
        return findByBusinessKey(outTradeNo, merchantId);
    }

    public NotifyRecord findByBusinessKey(String outTradeNo, Long merchantId) {
        List<NotifyRecord> records = jdbcTemplate.query("""
                SELECT id,merchant_id,out_trade_no,notify_url,body,status,
                       retry_count,next_retry_time,create_time
                FROM notify_record
                WHERE merchant_id=? AND out_trade_no=?
                LIMIT 1
                """, (result, rowNum) -> map(result), merchantId, outTradeNo);
        return records.isEmpty() ? null : records.get(0);
    }

    public NotifyRecord findById(Long id) {
        List<NotifyRecord> records = jdbcTemplate.query("""
                SELECT id,merchant_id,out_trade_no,notify_url,body,status,
                       retry_count,next_retry_time,create_time
                FROM notify_record
                WHERE id=?
                """, (result, rowNum) -> map(result), id);
        return records.isEmpty() ? null : records.get(0);
    }

    public void markSuccess(Long id) {
        jdbcTemplate.update("""
                UPDATE notify_record
                SET status='SUCCESS', next_retry_time=NULL
                WHERE id=?
                """, id);
    }

    public void markRetrying(Long id, int retryCount, LocalDateTime nextRetryTime) {
        jdbcTemplate.update("""
                UPDATE notify_record
                SET status='RETRYING', retry_count=?, next_retry_time=?
                WHERE id=?
                """, retryCount, Timestamp.valueOf(nextRetryTime), id);
    }

    public void markFailed(Long id, int retryCount) {
        jdbcTemplate.update("""
                UPDATE notify_record
                SET status='FAILED', retry_count=?, next_retry_time=NULL
                WHERE id=?
                """, retryCount, id);
    }

    private NotifyRecord map(java.sql.ResultSet result) throws java.sql.SQLException {
        Timestamp nextRetryTime = result.getTimestamp("next_retry_time");
        Timestamp createTime = result.getTimestamp("create_time");
        return NotifyRecord.builder()
                .id(result.getLong("id"))
                .merchantId(result.getLong("merchant_id"))
                .outTradeNo(result.getString("out_trade_no"))
                .notifyUrl(result.getString("notify_url"))
                .body(result.getString("body"))
                .status(result.getString("status"))
                .retryCount(result.getInt("retry_count"))
                .nextRetryTime(nextRetryTime == null ? null : nextRetryTime.toLocalDateTime())
                .createTime(createTime == null ? null : createTime.toLocalDateTime())
                .build();
    }
}
