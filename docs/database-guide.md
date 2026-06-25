# 数据库字典 — 分布式支付收银台 & 账务系统

> 每个库、每张表、每个字段的业务含义和设计原因。

---

## 总体架构

```
                     ┌─────────────────────────────┐
                     │     ShardingSphere-JDBC       │
                     │   按 merchant_id % 4 分库      │
                     │   按 merchant_id % 8 分表      │
                     └─────────────────────────────┘
                                  │
          ┌───────────────────────┼───────────────────────┐
          ▼                       ▼                       ▼                       ▼
    ┌──────────┐           ┌──────────┐           ┌──────────┐           ┌──────────┐
    │  MySQL   │           │  MySQL   │           │  MySQL   │           │  MySQL   │
    │   ds0    │           │   ds1    │           │   ds2    │           │   ds3    │
    │ :3306    │           │ :3307    │           │ :3308    │           │ :3309    │
    └──────────┘           └──────────┘           └──────────┘           └──────────┘
         │                      │                      │                      │
         ├── payment_account_0  ├── payment_account_0  ├── payment_account_0  ├── payment_account_0
         ├── payment_order_0    ├── payment_order_0    ├── payment_order_0    ├── payment_order_0
         ├── payment_merchant   │                      │                      │
         ├── payment_simulator  │                      │                      │
         └── xxl_job            │                      │                      │
```



---

## 一、非分片库（仅在 ds0）

### 1.1 数据库名：`payment_merchant`

> 用途：商户服务（merchant-service）的数据，不参与分片。

---

#### 表 `merchant` — 商户主表

每入驻一个商户就插入一行。

| 字段 | 类型 | 说明 | 业务含义 |
|------|------|------|---------|
| `id` | BIGINT | 主键 | 商户 ID，全局唯一，由 Snowflake 生成 |
| `merchant_no` | VARCHAR(32) | 商户编号（唯一） | 对外展示的商户号，格式 MCH + 时间戳，如 MCH202406190001 |
| `name` | VARCHAR(128) | 商户名称 | 商户公司名或品牌名，如"小明科技" |
| `status` | VARCHAR(16) | 状态 | ACTIVE=正常可用，DISABLED=已停用（停用后支付网关拒绝其请求） |
| `contact_email` | VARCHAR(128) | 联系人邮箱 | 通知接收邮箱，可为空 |
| `api_key` | VARCHAR(64) | API 密钥（唯一） | 商户调用支付网关的鉴权凭证，由平台生成，类似微信的商户 API Key |
| `create_time` | DATETIME | 创建时间 | 商户入驻时间 |
| `update_time` | DATETIME | 更新时间 | 最后修改时间，自动更新 |

**为什么 api_key 和 merchant_no 各建唯一约束？** api_key 用于接口鉴权（每次请求携带），merchant_no 用于商户对账编号（对外唯一标识），两者用途不同。

---

#### 表 `merchant_key` — 商户 RSA 密钥表

每个商户至少有一对 RSA 密钥用于请求签名。

| 字段 | 类型 | 说明 | 业务含义 |
|------|------|------|---------|
| `id` | BIGINT | 主键 | 自增 |
| `merchant_id` | BIGINT | 商户 ID | 关联 merchant 表 |
| `public_key` | TEXT | RSA 公钥（Base64） | 商户注册时生成的公钥，平台保存用来验签 |
| `key_type` | VARCHAR(16) | 密钥类型 | 固定为 RSA，未来可扩展国密 SM2 等 |
| `status` | VARCHAR(16) | 状态 | ACTIVE=使用中，INACTIVE=已废弃（密钥轮换时旧密钥标记此状态） |
| `create_time` | DATETIME | 创建时间 | 密钥生成时间 |

**RSA 签名流程：** 商户入驻 → 服务端生成一对 RSA 密钥 → 私钥返回给商户（仅此一次） → 公钥存储到此表 → 商户每次请求用私钥签名 → 网关用此表公钥验签。

---

#### 表 `rate_config` — 费率配置表

定义每个商户、每个渠道的支付手续费率。

| 字段 | 类型 | 说明 | 业务含义 |
|------|------|------|---------|
| `id` | BIGINT | 主键 | 自增 |
| `merchant_id` | BIGINT | 商户 ID | 哪个商户的费率（不同商户可能不同费率） |
| `channel_type` | VARCHAR(32) | 渠道类型 | WECHAT=微信 / ALIPAY=支付宝 / UNIONPAY=银联 |
| `fee_rate` | DECIMAL(5,4) | 费率 | 如 0.0038 表示 0.38%，即可 100 元扣 0.38 元手续费 |
| `status` | VARCHAR(16) | 状态 | ACTIVE=生效，INACTIVE=失效 |
| `create_time` | DATETIME | 创建时间 | — |
| `update_time` | DATETIME | 更新时间 | — |

**费率计算公式：** `商户实收 = 支付金额 × (1 - fee_rate)`。如支付 100 元，费率 0.38%，平台收 0.38 元，商户实收 99.62 元。

---

### 1.2 数据库名：`payment_simulator`

> 用途：渠道模拟器（channel-simulator）的数据，独立于业务库。

---

#### 表 `channel_order` — 模拟渠道订单表

模拟器收到支付请求后记录在此表，模拟真实渠道的订单记录。

| 字段 | 类型 | 说明 | 业务含义 |
|------|------|------|---------|
| `id` | BIGINT | 主键 | Snowflake |
| `channel_order_no` | VARCHAR(64) | 渠道订单号（唯一） | 模拟微信/支付宝生成的订单号，如 WX202406190001 |
| `out_trade_no` | VARCHAR(64) | 商户订单号 | 对应商户请求的订单号，用于查单时匹配 |
| `amount` | DECIMAL(18,2) | 金额 | 交易金额（元） |
| `status` | VARCHAR(16) | 状态 | SUCCESS=成功 / FAIL=失败 / UNKNOWN=不确定（模拟超时场景） |
| `channel_type` | VARCHAR(32) | 渠道类型 | 默认 MOCK，可改为 WECHAT 区分模拟行为 |
| `create_time` | DATETIME | 创建时间 | — |

**三种状态的意义：**
- SUCCESS → 网关继续 TCC Confirm 扣款
- FAIL → 网关直接返回失败，不扣款
- UNKNOWN → 网关返回 202 处理中，然后轮询查单确认最终结果

---

#### 表 `simulator_config` — 模拟器行为配置表

运行时动态控制模拟器的行为，不需要重启服务。

| 字段 | 类型 | 说明 | 业务含义 |
|------|------|------|---------|
| `id` | INT | 主键 | 自增 |
| `channel_type` | VARCHAR(32) | 渠道类型 | DEFAULT=所有渠道 / WECHAT=仅微信 |
| `delay_ms` | INT | 模拟延迟（毫秒） | 模拟网络延迟，0=无延迟。压测时可设为 50ms |
| `success_rate` | DECIMAL(3,2) | 成功率 | 0.00 ~ 1.00，如 0.80 表示 80% 概率返回 SUCCESS |
| `unknown_rate` | DECIMAL(3,2) | UNKNOWN 率 | 0.00 ~ 1.00，模拟超时不明确状态的场景 |
| `status` | VARCHAR(16) | 状态 | ACTIVE=生效 |
| `create_time` | DATETIME | — | — |
| `update_time` | DATETIME | — | — |

**三率之和逻辑：**
- 收到请求 → 随机 0~1 的数
- 落在 [0, success_rate) → 返回 SUCCESS
- 落在 [success_rate, success_rate + unknown_rate) → 返回 UNKNOWN
- 超出 → 返回 FAIL
- 默认配置：80% SUCCESS / 10% UNKNOWN / 10% FAIL

---

## 二、分片数据库（ds0 ~ ds3 各有一份）

### 2.1 数据库名：`payment_account_0`（4 个实例各有此库）

> 用途：账户服务（account-service）的核心数据，是整个系统中最重要的库。

---

#### 表 `account_0 ~ account_7` — 账户表（8 张）

每个商户在 8 张表中的某一张拥有一行记录。

| 字段 | 类型 | 说明 | 业务含义 |
|------|------|------|---------|
| `id` | BIGINT | 账户 ID（主键） | Snowflake 生成 |
| `merchant_id` | BIGINT | 商户 ID（唯一、分片键） | 一个商户只有一条账户记录 |
| `balance` | DECIMAL(18,2) | 账户总余额 | 含冻结金额，如 10000.00 = 1 万元 |
| `frozen_amount` | DECIMAL(18,2) | 冻结金额 | TCC Try 阶段预扣的金额，暂不可用 |
| `version` | INT | 乐观锁版本号 | 每次 UPDATE 必须 `WHERE version = ?`，失败则并发冲突 |
| `create_time` | DATETIME | 创建时间 | — |
| `update_time` | DATETIME | 更新时间 | — |

**字段关系：** `可用余额 = balance - frozen_amount`

**乐观锁工作原理：**
```sql
-- 扣款时（TCC Confirm）
UPDATE account_0
SET balance = balance - 100, frozen_amount = frozen_amount - 100, version = version + 1
WHERE merchant_id = 10001 AND version = 3;  -- version 必须是当前值

-- 如果影响行数 = 0 → 说明有其他线程同时修改了，抛出重试
```

**为什么余额不用 DECIMAL(20,4) 而用 (18,2)？** 人民币最小单位是分（0.01），两位小数足够。18 位整数能存 999 万亿，够用。

---

#### 表 `transaction_0 ~ transaction_7` — 交易记录表（8 张）

记录每一次账户变动的摘要，相当于"交易流水总账"。

| 字段 | 类型 | 说明 | 业务含义 |
|------|------|------|---------|
| `id` | BIGINT | 主键 | Snowflake |
| `txn_id` | VARCHAR(32) | 交易流水号（索引） | 全局唯一，关联 journal_entry 表 |
| `merchant_id` | BIGINT | 商户 ID（分片键） | 这个交易属于哪个商户 |
| `amount` | DECIMAL(18,2) | 交易金额 | — |
| `txn_type` | VARCHAR(32) | 交易类型 | PAY=支付扣款 / REFUND=退款 / FREEZE=冻结 / UNFREEZE=解冻 / RECHARGE=充值 |
| `out_trade_no` | VARCHAR(64) | 外部订单号（索引） | 幂等键：同一订单号不会重复处理 |
| `status` | VARCHAR(16) | 交易状态 | PENDING / SUCCESS / FAILED |
| `create_time` | DATETIME | 创建时间 | — |

**和 journal_entry 的关系：** transaction 是"一笔交易"，journal_entry 是这笔交易在借贷双方的"流水记录"。一笔 PAY → 1 条 transaction + 2 条 journal_entry（借+贷）。

---

#### 表 `journal_entry_0 ~ journal_entry_7` — 复式记账流水表（8 张）

这是整个系统最核心的表，体现"每一笔钱从哪来到哪去"。

| 字段 | 类型 | 说明 | 业务含义 |
|------|------|------|---------|
| `id` | BIGINT | 主键 | Snowflake |
| `txn_id` | VARCHAR(32) | 交易流水号（索引） | 关联 transaction 表，同一笔交易的两条流水共享此号 |
| `debit_account_id` | BIGINT | 借方账户 ID | **钱从哪个账户出**（资产减少方） |
| `credit_account_id` | BIGINT | 贷方账户 ID | **钱进哪个账户**（资产增加方） |
| `amount` | DECIMAL(18,2) | 交易金额 | — |
| `dr_cr_flag` | CHAR(1) | 借贷标识 | D=此条是借方记录 / C=此条是贷方记录 |
| `txn_type` | VARCHAR(32) | 交易类型 | 同 transaction 表 |
| `txn_time` | DATETIME | 交易时间 | — |
| `merchant_id` | BIGINT | 商户 ID（分片键） | — |

**为什么每笔交易要两条记录？举个例子：**

商户 A（账户 10001）向商户 B（账户 10002）付款 100 元：

| txn_id | debit_account_id | credit_account_id | amount | dr_cr_flag | 解释 |
|--------|-----------------|-------------------|--------|------------|------|
| TXN001 | 10001 | 10002 | 100.00 | D | 借方：商户 A 账户减少 100 元 |
| TXN001 | 10001 | 10002 | 100.00 | C | 贷方：商户 B 账户增加 100 元 |

**借贷平衡检查：** 查询 `WHERE txn_id = 'TXN001'` → 借方总金额 = 贷方总金额 → 如果不相等，说明有 Bug。

---

### 2.2 数据库名：`payment_order_0`（4 个实例各有此库）

> 用途：订单服务（order-service）的数据。

---

#### 表 `order` — 订单表

| 字段 | 类型 | 说明 | 业务含义 |
|------|------|------|---------|
| `id` | BIGINT | 主键 | Snowflake |
| `order_no` | VARCHAR(32) | 内部订单号（索引） | 平台内部唯一订单号 |
| `out_trade_no` | VARCHAR(64) | 商户订单号（索引） | 商户侧订单号，幂等去重用 |
| `merchant_id` | BIGINT | 商户 ID（分片键） | — |
| `amount` | DECIMAL(18,2) | 订单金额 | — |
| `status` | VARCHAR(16) | 订单状态 | CREATED→PAID→SETTLED / REFUNDING→REFUNDED / CLOSED |
| `channel_order_no` | VARCHAR(64) | 渠道订单号 | 关联 channel_order 表，可追溯渠道侧记录 |
| `create_time` | DATETIME | 创建时间 | — |
| `update_time` | DATETIME | 更新时间 | — |

**订单状态流转：**

```
CREATED（已创建，等支付）
  → 支付成功 → PAID（已支付）
    → 自动结算 / 手动确认 → SETTLED（已结算，终态，不可退款）
    → 发起退款 → REFUNDING（退款中）
      → 退款成功 → REFUNDED（已退款，终态）
      → 退款失败 → 回退到 PAID
  → 30 分钟未支付 → CLOSED（已关闭，终态）
```

---

#### 表 `refund_order` — 退款单表

| 字段 | 类型 | 说明 | 业务含义 |
|------|------|------|---------|
| `id` | BIGINT | 主键 | Snowflake |
| `refund_no` | VARCHAR(32) | 内部退款单号（索引） | 平台内部唯一 |
| `out_refund_no` | VARCHAR(64) | 商户退款单号 | 商户侧退款单号，幂等去重用 |
| `origin_order_no` | VARCHAR(32) | 原订单号 | 关联 order 表，退的是哪笔订单 |
| `merchant_id` | BIGINT | 商户 ID | — |
| `refund_amount` | DECIMAL(18,2) | 退款金额 | 不超过原订单金额 |
| `status` | VARCHAR(16) | 退款状态 | REFUNDING=退款中 / REFUNDED=已退款 / FAILED=失败 |
| `create_time` | DATETIME | — | — |
| `update_time` | DATETIME | — | — |

**退款幂等：** `out_refund_no` 如果重复提交，直接返回第一次的结果，不重复退款。

---

## 三、一张图看懂：一笔完整支付经过哪些表

```
商户 POST /api/v1/pay  { outTradeNo: "M001", amount: 100 }

① 网关：检查幂等 → 无重复 → 放行

② 网关调用模拟器 → channel_order 表插入一条（status=SUCCESS）

③ 网关调用 TCC Try → transaction 表插入一条（txn_type=FREEZE）
                        → account 表 frozen_amount + 100

④ 网关调用 TCC Confirm → account 表 balance - 100, frozen_amount - 100
                         → transaction 表插入一条（txn_type=PAY）
                         → journal_entry 表插入两条：
                             D 记录：debit=商户, credit=平台, amount=100
                             C 记录：debit=商户, credit=平台, amount=100

⑤ MQ 发送 pay-success 事件 →

⑥ order-service 消费 → order 表插入一条（status=PAID → SETTLED）

⑦ notification-service 消费 → 构造签名回调 → POST 商户 notifyUrl

⑧ Canal 监听 Binlog → 发现 journal_entry 新纪录 → Kafka →
   对账服务 → 比对 channel_order 表 → 一致则标记完成
```

---

## 四、重要设计原则

| 原则 | 说明 |
|------|------|
| **余额不缓存** | account 表走 DB 直读直写，保证强一致，避免缓存脏读造成资损 |
| **订单可缓存** | order 表查询可走 Redis（5min TTL），短暂不一致不影响资金安全 |
| **流水只追加** | journal_entry 和 transaction 只 INSERT 不 UPDATE，保证审计可追溯 |
| **借贷必须平衡** | 每笔交易的 D 和 C 两条金额相等，日终对账可自动校验 |
| **分片键选 merchant_id** | 所有查询都带商户维度，路由精准无须广播 |
