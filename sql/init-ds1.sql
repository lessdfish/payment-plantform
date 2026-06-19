-- ============================================================
-- 分片数据源 ds1 初始化脚本（端口：3307）
-- ds1~ds3 只需要分片表，非分片表（merchant/simulator）仅在 ds0
-- ============================================================

-- 账户服务数据库（分片 1 / 4）
CREATE DATABASE IF NOT EXISTS payment_account_0 DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
USE payment_account_0;

CREATE TABLE IF NOT EXISTS account_0 (
    id BIGINT PRIMARY KEY, merchant_id BIGINT NOT NULL, balance DECIMAL(18,2) NOT NULL DEFAULT 0.00,
    frozen_amount DECIMAL(18,2) NOT NULL DEFAULT 0.00, version INT NOT NULL DEFAULT 0,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_merchant_id (merchant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS account_1 LIKE account_0;
CREATE TABLE IF NOT EXISTS account_2 LIKE account_0;
CREATE TABLE IF NOT EXISTS account_3 LIKE account_0;
CREATE TABLE IF NOT EXISTS account_4 LIKE account_0;
CREATE TABLE IF NOT EXISTS account_5 LIKE account_0;
CREATE TABLE IF NOT EXISTS account_6 LIKE account_0;
CREATE TABLE IF NOT EXISTS account_7 LIKE account_0;

CREATE TABLE IF NOT EXISTS transaction_0 (
    id BIGINT PRIMARY KEY, txn_id VARCHAR(32) NOT NULL, merchant_id BIGINT NOT NULL,
    amount DECIMAL(18,2) NOT NULL, txn_type VARCHAR(32) NOT NULL,
    out_trade_no VARCHAR(64) DEFAULT NULL, status VARCHAR(16) DEFAULT NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_merchant_id (merchant_id), INDEX idx_txn_id (txn_id), INDEX idx_out_trade_no (out_trade_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS transaction_1 LIKE transaction_0;
CREATE TABLE IF NOT EXISTS transaction_2 LIKE transaction_0;
CREATE TABLE IF NOT EXISTS transaction_3 LIKE transaction_0;
CREATE TABLE IF NOT EXISTS transaction_4 LIKE transaction_0;
CREATE TABLE IF NOT EXISTS transaction_5 LIKE transaction_0;
CREATE TABLE IF NOT EXISTS transaction_6 LIKE transaction_0;
CREATE TABLE IF NOT EXISTS transaction_7 LIKE transaction_0;

CREATE TABLE IF NOT EXISTS journal_entry_0 (
    id BIGINT PRIMARY KEY, txn_id VARCHAR(32) NOT NULL, debit_account_id BIGINT NOT NULL,
    credit_account_id BIGINT NOT NULL, amount DECIMAL(18,2) NOT NULL, dr_cr_flag CHAR(1) NOT NULL,
    txn_type VARCHAR(32) NOT NULL, txn_time DATETIME NOT NULL, merchant_id BIGINT NOT NULL,
    INDEX idx_merchant_id (merchant_id), INDEX idx_txn_id (txn_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS journal_entry_1 LIKE journal_entry_0;
CREATE TABLE IF NOT EXISTS journal_entry_2 LIKE journal_entry_0;
CREATE TABLE IF NOT EXISTS journal_entry_3 LIKE journal_entry_0;
CREATE TABLE IF NOT EXISTS journal_entry_4 LIKE journal_entry_0;
CREATE TABLE IF NOT EXISTS journal_entry_5 LIKE journal_entry_0;
CREATE TABLE IF NOT EXISTS journal_entry_6 LIKE journal_entry_0;
CREATE TABLE IF NOT EXISTS journal_entry_7 LIKE journal_entry_0;

-- 订单服务数据库（分片 1 / 4）
CREATE DATABASE IF NOT EXISTS payment_order_0 DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
USE payment_order_0;

CREATE TABLE IF NOT EXISTS `order` (
    id BIGINT PRIMARY KEY, order_no VARCHAR(32) NOT NULL, out_trade_no VARCHAR(64) NOT NULL,
    merchant_id BIGINT NOT NULL, amount DECIMAL(18,2) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'CREATED', channel_order_no VARCHAR(64) DEFAULT NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_merchant_id (merchant_id), INDEX idx_order_no (order_no), INDEX idx_out_trade_no (out_trade_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS refund_order (
    id BIGINT PRIMARY KEY, refund_no VARCHAR(32) NOT NULL, out_refund_no VARCHAR(64) NOT NULL,
    origin_order_no VARCHAR(32) NOT NULL, merchant_id BIGINT NOT NULL,
    refund_amount DECIMAL(18,2) NOT NULL, status VARCHAR(16) NOT NULL DEFAULT 'REFUNDING',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_merchant_id (merchant_id), INDEX idx_refund_no (refund_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
