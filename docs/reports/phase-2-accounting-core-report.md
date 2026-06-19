# Phase 2 完成报告：账务核心

> **日期：** 2026-06-19
> **阶段：** Phase 2 — 账务核心
> **结果：** ✅ 全量编译通过

---

## 一、改动范围

### account-service（20 个文件）

| 层 | 文件 | 关键内容 |
|---|------|---------|
| Entity | Account.java | balance + frozenAmount + version(乐观锁) |
| Entity | Transaction.java | txnId + txnType(PAY/REFUND/FREEZE/UNFREEZE) |
| Entity | JournalEntry.java | debitAccountId + creditAccountId + drCrFlag(D/C) |
| Repository | AccountRepository, TransactionRepository, JournalEntryRepository | JPA |
| DTO | TryRequest/Response, ConfirmRequest, CancelRequest | 已移至 common |
| Service | TccService | tryFreeze / confirm / cancel |
| Service | TccServiceImpl | **防超卖 + 幂等 + 乐观锁** |
| Service | JournalService / impl | 借贷各一条，金额相等 |
| Service | AccountService / impl | 余额查询 + 充值（自动创建账户） |
| Controller | AccountController | GET balance / POST recharge |
| Controller | TccController | POST try / confirm / cancel |
| Config | ShardingConfig.java | 4库×8表 YAML 配置 |
| Producer | TxnLogProducer.java | Kafka txn-log（预留） |

### order-service（12 个文件）

| 层 | 文件 | 关键内容 |
|---|------|---------|
| Entity | Order, RefundOrder | 订单表 + 退款单表 |
| Repository | OrderRepository, RefundOrderRepository | JPA |
| Service | OrderService / impl | create / getByOrderNo / updateStatus |
| Service | RefundService / impl | apply（含幂等） |
| Controller | OrderController, RefundController | REST API |
| Consumer | PaySuccessConsumer | 消费 RocketMQ pay-success |
| Producer | RefundProducer | 发送 refund-notify |
| Config | ShardingConfig.java | 4库 × 订单表 |

### payment-gateway 改造（3 个文件）

| 文件 | 改动 |
|------|------|
| AccountClient.java | 桩 → 真实调用 TCC Try/Confirm/Cancel |
| OrderClient.java | 桩 → RocketMQ 发送 pay-success 事件 |
| PayServiceImpl.java | handleSuccess 加入 TCC + MQ + 异常补偿 Cancel |

### common 新增（4 个文件）

```
TryRequest, ConfirmRequest, CancelRequest → dto/request/
TryResponse → dto/response/
```

---

## 二、关键技术实现

### TCC 三阶段

```
Try  → 冻结 frozenAmount（balance 不变）
        防超卖: WHERE balance - frozenAmount >= amount
Confirm → 余额实扣 + 复式借贷流水(2条)
        幂等: 通过 txnId + _CONFIRM 查重
Cancel  → 释放 frozenAmount
        幂等: 通过 txnId + _CANCEL 查重
```

### 分库分表

| 维度 | 规则 |
|------|------|
| 分库 | merchant_id % 4 → ds{0-3}（4 个 MySQL 实例） |
| 分表 | merchant_id % 8 → _0-_7（8 张表） |
| 总片数 | 4 × 8 = 32 个分片 |
| 配置方式 | YAML → YamlShardingSphereDataSourceFactory |

---

## 三、编译结果

```
BUILD SUCCESS — 9/9 模块
```

---

## 四、验收步骤

### 验收 1：启动 account-service

1. 确保 Docker 在运行
2. IDEA 启动 `AccountApplication` (8081)
3. 检查：Tomcat started on port 8081

### 验收 2：运行 AccountServiceTest

IDEA 右键 `AccountServiceTest` → Run，预期 7 个测试全部通过：

| # | 用例 | 预期 |
|---|------|------|
| TC01 | 充值创建账户 | 返回 code=0 |
| TC02 | 查询余额 | balance=10000 |
| TC03 | TCC Try | 返回 tccId |
| TC04 | TCC Confirm | 余额=9500，生成复式流水 |
| TC05 | TCC Cancel | 冻结释放，余额不变 |
| TC06 | 幂等 Confirm | 余额不再变化 |
| TC07 | 余额不足 | 返回 42201 |

### 验收 3：全链路联调

按顺序启动 4 个服务：merchant(8085), simulator(8086), account(8081), gateway(8080)

运行 `PaymentIntegrationTest` — 支付请求会走完整链路：
验签→风控→路由→渠道→**TCC Try→TCC Confirm**→MQ→订单创建→幂等

---

## 五、遇到的问题 & 修复

| 问题 | 修复 |
|------|------|
| ShardingSphereDriverFactory 导入失败 | 改用 YamlShardingSphereDataSourceFactory + YAML |
| 跨模块 DTO 引用 | TCC DTO 移至 payment-common |
| PayServiceImpl 缺 TryResponse import | 添加 import |
| Windows jar 文件锁定 | taskkill java 进程后重新编译 |

## 六、下一步

Phase 3：对账 & 通知
- reconciliation-service + Canal + XXL-JOB
- notification-service + 回调退避重试
- Grafana Dashboard
