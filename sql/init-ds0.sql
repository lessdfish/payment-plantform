-- ============================================================
-- 分布式支付收银台 — 分片数据源 ds0 初始化脚本
-- 端口：3306
-- ============================================================

CREATE USER IF NOT EXISTS 'canal'@'%' IDENTIFIED BY 'canal';
GRANT SELECT, REPLICATION SLAVE, REPLICATION CLIENT ON *.* TO 'canal'@'%';
FLUSH PRIVILEGES;

-- XXL-JOB 调度中心数据库
CREATE DATABASE IF NOT EXISTS xxl_job DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;

-- 账户服务数据库（分片 0 / 4）
CREATE DATABASE IF NOT EXISTS payment_account_0 DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
USE payment_account_0;

-- 账户表（account_0 ~ account_7，按 merchant_id % 8 分片）
CREATE TABLE IF NOT EXISTS account_0 (
    id BIGINT PRIMARY KEY COMMENT '账户ID（Snowflake）',
    merchant_id BIGINT NOT NULL COMMENT '商户ID（分片键）',
    balance DECIMAL(18,2) NOT NULL DEFAULT 0.00 COMMENT '账户总余额',
    frozen_amount DECIMAL(18,2) NOT NULL DEFAULT 0.00 COMMENT '冻结金额',
    version INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_merchant_id (merchant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='账户表_分片0';

CREATE TABLE IF NOT EXISTS account_1 LIKE account_0;
CREATE TABLE IF NOT EXISTS account_2 LIKE account_0;
CREATE TABLE IF NOT EXISTS account_3 LIKE account_0;
CREATE TABLE IF NOT EXISTS account_4 LIKE account_0;
CREATE TABLE IF NOT EXISTS account_5 LIKE account_0;
CREATE TABLE IF NOT EXISTS account_6 LIKE account_0;
CREATE TABLE IF NOT EXISTS account_7 LIKE account_0;

-- 交易记录表（transaction_0 ~ transaction_7）
CREATE TABLE IF NOT EXISTS transaction_0 (
    id BIGINT PRIMARY KEY COMMENT '主键ID（Snowflake）',
    txn_id VARCHAR(32) NOT NULL COMMENT '交易流水号',
    merchant_id BIGINT NOT NULL COMMENT '商户ID（分片键）',
    amount DECIMAL(18,2) NOT NULL COMMENT '交易金额',
    txn_type VARCHAR(32) NOT NULL COMMENT '交易类型：PAY/REFUND/FREEZE/UNFREEZE/RECHARGE',
    out_trade_no VARCHAR(64) DEFAULT NULL COMMENT '外部订单号（幂等键）',
    status VARCHAR(16) DEFAULT NULL COMMENT '交易状态',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_merchant_id (merchant_id),
    UNIQUE KEY uk_txn_id (txn_id),
    INDEX idx_out_trade_no (out_trade_no),
    UNIQUE KEY uk_merchant_trade_type (merchant_id, out_trade_no, txn_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='交易记录表_分片0';

CREATE TABLE IF NOT EXISTS transaction_1 LIKE transaction_0;
CREATE TABLE IF NOT EXISTS transaction_2 LIKE transaction_0;
CREATE TABLE IF NOT EXISTS transaction_3 LIKE transaction_0;
CREATE TABLE IF NOT EXISTS transaction_4 LIKE transaction_0;
CREATE TABLE IF NOT EXISTS transaction_5 LIKE transaction_0;
CREATE TABLE IF NOT EXISTS transaction_6 LIKE transaction_0;
CREATE TABLE IF NOT EXISTS transaction_7 LIKE transaction_0;

-- 复式记账流水表（journal_entry_0 ~ journal_entry_7）
CREATE TABLE IF NOT EXISTS journal_entry_0 (
    id BIGINT PRIMARY KEY COMMENT '主键ID（Snowflake）',
    txn_id VARCHAR(32) NOT NULL COMMENT '交易流水号',
    debit_account_id BIGINT NOT NULL COMMENT '借方账户ID',
    credit_account_id BIGINT NOT NULL COMMENT '贷方账户ID',
    amount DECIMAL(18,2) NOT NULL COMMENT '交易金额',
    dr_cr_flag CHAR(1) NOT NULL COMMENT '借贷标识：D=借方 C=贷方',
    txn_type VARCHAR(32) NOT NULL COMMENT '交易类型',
    txn_time DATETIME NOT NULL COMMENT '交易时间',
    merchant_id BIGINT NOT NULL COMMENT '商户ID（分片键）',
    out_trade_no VARCHAR(64) DEFAULT NULL COMMENT '商户订单号（对账键）',
    INDEX idx_merchant_id (merchant_id),
    INDEX idx_txn_id (txn_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='复式记账流水表_分片0';

CREATE TABLE IF NOT EXISTS journal_entry_1 LIKE journal_entry_0;
CREATE TABLE IF NOT EXISTS journal_entry_2 LIKE journal_entry_0;
CREATE TABLE IF NOT EXISTS journal_entry_3 LIKE journal_entry_0;
CREATE TABLE IF NOT EXISTS journal_entry_4 LIKE journal_entry_0;
CREATE TABLE IF NOT EXISTS journal_entry_5 LIKE journal_entry_0;
CREATE TABLE IF NOT EXISTS journal_entry_6 LIKE journal_entry_0;
CREATE TABLE IF NOT EXISTS journal_entry_7 LIKE journal_entry_0;

-- 订单服务数据库
CREATE DATABASE IF NOT EXISTS payment_order_0 DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
USE payment_order_0;

-- 订单表
CREATE TABLE IF NOT EXISTS payment_order (
    id BIGINT PRIMARY KEY COMMENT '订单ID（Snowflake）',
    order_no VARCHAR(32) NOT NULL COMMENT '内部订单号',
    out_trade_no VARCHAR(64) NOT NULL COMMENT '商户订单号',
    merchant_id BIGINT NOT NULL COMMENT '商户ID（分片键）',
    amount DECIMAL(18,2) NOT NULL COMMENT '订单金额',
    status VARCHAR(16) NOT NULL DEFAULT 'CREATED' COMMENT '订单状态',
    channel_order_no VARCHAR(64) DEFAULT NULL COMMENT '渠道订单号',
    notify_url VARCHAR(512) DEFAULT NULL COMMENT '商户回调地址',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_merchant_id (merchant_id),
    INDEX idx_order_no (order_no),
    INDEX idx_out_trade_no (out_trade_no),
    UNIQUE KEY uk_merchant_out_trade (merchant_id, out_trade_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订单表';

-- 退款单表
CREATE TABLE IF NOT EXISTS refund_order (
    id BIGINT PRIMARY KEY COMMENT '退款单ID（Snowflake）',
    refund_no VARCHAR(32) NOT NULL COMMENT '内部退款单号',
    out_refund_no VARCHAR(64) NOT NULL COMMENT '商户退款单号',
    origin_order_no VARCHAR(32) NOT NULL COMMENT '原订单号',
    merchant_id BIGINT NOT NULL COMMENT '商户ID',
    refund_amount DECIMAL(18,2) NOT NULL COMMENT '退款金额',
    status VARCHAR(16) NOT NULL DEFAULT 'REFUNDING' COMMENT '退款状态',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_merchant_id (merchant_id),
    INDEX idx_refund_no (refund_no),
    UNIQUE KEY uk_merchant_out_refund (merchant_id, out_refund_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='退款单表';

-- 商户服务数据库
CREATE DATABASE IF NOT EXISTS payment_merchant DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
USE payment_merchant;

-- 商户表
CREATE TABLE IF NOT EXISTS merchant (
    id BIGINT PRIMARY KEY COMMENT '商户ID',
    merchant_no VARCHAR(32) NOT NULL COMMENT '商户编号',
    name VARCHAR(128) NOT NULL COMMENT '商户名称',
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE' COMMENT '状态：ACTIVE/DISABLED',
    contact_email VARCHAR(128) DEFAULT NULL COMMENT '联系人邮箱',
    api_key VARCHAR(64) NOT NULL COMMENT 'API密钥',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_merchant_no (merchant_no),
    UNIQUE KEY uk_api_key (api_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商户表';

-- 商户密钥表
CREATE TABLE IF NOT EXISTS merchant_key (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键',
    merchant_id BIGINT NOT NULL COMMENT '商户ID',
    public_key TEXT NOT NULL COMMENT 'RSA公钥（Base64）',
    key_type VARCHAR(16) NOT NULL DEFAULT 'RSA' COMMENT '密钥类型',
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE' COMMENT '状态',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_merchant_id (merchant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商户密钥表';

-- 费率配置表
CREATE TABLE IF NOT EXISTS rate_config (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键',
    merchant_id BIGINT NOT NULL COMMENT '商户ID',
    channel_type VARCHAR(32) NOT NULL COMMENT '渠道类型：WECHAT/ALIPAY/UNIONPAY',
    fee_rate DECIMAL(5,4) NOT NULL COMMENT '费率（如0.0038=0.38%）',
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE' COMMENT '状态',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_merchant_channel (merchant_id, channel_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='费率配置表';

-- 渠道模拟器数据库
CREATE DATABASE IF NOT EXISTS payment_simulator DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
USE payment_simulator;

-- 模拟渠道订单表
CREATE TABLE IF NOT EXISTS channel_order (
    id BIGINT PRIMARY KEY COMMENT '订单ID',
    channel_order_no VARCHAR(64) NOT NULL COMMENT '渠道订单号',
    out_trade_no VARCHAR(64) NOT NULL COMMENT '商户订单号',
    amount DECIMAL(18,2) NOT NULL COMMENT '金额',
    status VARCHAR(16) NOT NULL COMMENT '状态：SUCCESS/FAIL/UNKNOWN',
    channel_type VARCHAR(32) DEFAULT 'MOCK' COMMENT '渠道类型',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_channel_order_no (channel_order_no),
    UNIQUE KEY uk_out_trade_no (out_trade_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='模拟渠道订单表';

-- 模拟器配置表
CREATE TABLE IF NOT EXISTS simulator_config (
    id INT PRIMARY KEY AUTO_INCREMENT COMMENT '主键',
    channel_type VARCHAR(32) NOT NULL DEFAULT 'DEFAULT' COMMENT '渠道类型',
    delay_ms INT NOT NULL DEFAULT 0 COMMENT '模拟延迟（毫秒）',
    success_rate DECIMAL(3,2) NOT NULL DEFAULT 0.80 COMMENT '成功率',
    unknown_rate DECIMAL(3,2) NOT NULL DEFAULT 0.10 COMMENT 'UNKNOWN率',
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='模拟器配置表';

-- 插入默认配置（80% 成功 / 10% UNKNOWN / 10% FAIL）
INSERT INTO simulator_config (channel_type, delay_ms, success_rate, unknown_rate)
VALUES ('DEFAULT', 50, 0.80, 0.10);
