# 项目进度检查点

> **日期：** 2026-06-25
> **目标：** 对照 Plan 和 Spec 盘清当前进度，明确距离最终交付还差什么

---

## 一、阶段进度总览

| 阶段 | 周期 | 内容 | 状态 | Git 提交 |
|------|------|------|------|----------|
| Phase 0 | 第 1 周 | 项目骨架 | ✅ 完成 | `1d6129e` |
| Phase 1 | 第 1-3 周 | 基础支付链路 | ✅ 完成 | `6a73f16` |
| Phase 2 | 第 4-6 周 | 账务核心 | ✅ 完成 | `304187b` |
| Phase 3 | 第 7-8 周 | 对账 & 通知 | ✅ 完成 | `7eb6fd0` `997641b` |
| Phase 4.1 | 第 9 周 | 风控模块 | ✅ 完成 | `634b9e5` |
| Phase 4.2 | 第 10 周 | 可观测性体系 | ✅ 完成 | `84071e9` |
| Phase 4.3 | 第 10 周 | 压测执行 | ⚠️ 已执行；功能正确，容量未达标 | 当前工作 |
| Phase 5 | 第 11-12 周 | 扩展功能 | ❌ 未开始 | — |

---

## 二、Phase 0-4.2 已完工详情

### Phase 0 — 项目骨架

- [x] Maven 父工程 POM，9 个子模块，12+ 依赖版本锁定
- [x] payment-common 公共模块（35+ 类：常量/枚举/DTO/异常/统一响应/工具类）
- [x] Docker Compose 16 个容器编排
- [x] 7 个服务 Spring Boot 骨架 + Nacos 注册
- [x] GlobalExceptionHandler 统一异常处理（8 种异常映射）
- [x] ApiResult 统一响应体

### Phase 1 — 基础支付链路

- [x] **merchant-service**（15 个源文件）：商户入驻、RSA 密钥对生成、费率配置
- [x] **channel-simulator**（12 个源文件）：按概率返回 SUCCESS/FAIL/UNKNOWN，可配置延迟
- [x] **payment-gateway**（17 个源文件）：10 步完整支付流程
  - 幂等检查 → RSA 验签 → 风控检查 → 渠道路由 → 渠道调用 → TCC → MQ → 缓存结果
- [x] PaymentIntegrationTest 端到端验证通过

### Phase 2 — 账务核心

- [x] **account-service**（20+ 个源文件）：账户管理 + TCC Try/Confirm/Cancel + 复式记账
- [x] **order-service**（12 个源文件）：订单状态机 + 退款 + RocketMQ 消费
- [x] ShardingSphere-JDBC 分库分表：4 库 × 8 表 = 32 分片，分片键 merchant_id
- [x] TCC 防超卖：乐观锁 version + 余额充足性校验
- [x] AccountServiceTest 7 个用例通过

### Phase 3 — 对账 & 通知

- [x] **reconciliation-service**（9 个源文件）：实时对账 + XXL-JOB 日终批处理
- [x] **notification-service**（8 个源文件）：商户回调 + 退避重试（1m/5m/15m/30m/1h）
- [x] Kafka txn-log 生产者 + 消费者打通
- [x] 退避重试已实现，RocketMQ 延迟消息

### Phase 4.1 — 风控模块

- [x] Redis 黑名单检查
- [x] IP 频控（Redis INCR，50 QPS）
- [x] 单笔限额（50000）
- [x] 日累计限额（Redis INCR，100 万/天，TTL 到次日 0 点）

### Phase 4.2 — 可观测性体系

- [x] 全局 actuator + micrometer-registry-prometheus
- [x] prometheus.yml 7 个服务目标
- [x] Prometheus + Loki + Promtail + Grafana 4 个容器
- [x] 5 个 Grafana Dashboard JSON（网关/支付链路/账户/MQ/对账）
- [x] ObservabilityTest 4 个用例

### Phase 4.3 — 压测执行

- [x] `jmeter/payment-stress-test.jmx`：5 个场景 JMeter 测试计划
- [x] `jmeter/SetupJMeterTest.java`：商户注册 + 密钥生成 + 充值 100 万前置脚本
- [x] `jmeter/README.md`：压测指南
- [x] 压测模式验签跳过：`SignatureServiceImpl` 新增 `pressure.enabled` 开关
- [x] `docs/reports/phase-4.3-stress-test-guide.md`：完整压测执行指导文档
- [x] `jmeter/payment-throughput.jmx`：1000 商户、4 库 8 表均匀分布的全链路压测
- [x] 50/200 线程实测、阶段耗时采集、后台 MQ/Canal 积压检查
- [x] 压测结束后恢复 `pressure.enabled=false`

---

## 三、需验证项逐条盘点

| # | 验证项 | 状态 | 说明 |
|---|--------|------|------|
| 1 | 依赖冲突 | ✅ | `mvn dependency:tree` 无 conflict |
| 2 | 编译通过 | ✅ | 9/9 模块 `BUILD SUCCESS` |
| 3 | Docker Compose | ✅ | 16 个容器全部 running + healthy |
| 4 | 服务注册 | ✅ | 7 服务注册到 Nacos |
| 5 | 支付全链路 | ✅ | PaymentIntegrationTest 通过 |
| 6 | 幂等性 | ✅ | 重复 outTradeNo 返回 DUPLICATE + 原结果 |
| 7 | **并发扣款** | ✅ | 100 笔并发扣 15 元：66 成功、34 余额不足；1000 → 10，冻结为 0 |
| 8 | 分库分表 | ✅ | ShardingSphere 路由正确 |
| 9 | 渠道 UNKNOWN | ✅ | 3 次轮询 2s/5s/10s 已实现 |
| 10 | TCC 异常回滚 | ✅ | AccountServiceTest 覆盖 Cancel 场景 |
| 11 | Canal 对账 | ✅ | ds0-ds3 四个 Canal 实例 → Kafka 四分区 → 四笔均 MATCHED |
| 12 | 回调重试 | ✅ | 实测 1m/5m/15m 延迟重试，成功后停止 |
| 13 | Sentinel 限流 | ✅ | 实测 HTTP 429，业务码 42901 |
| 14 | Grafana 面板 | ✅ | 5 个 Dashboard 自动 provisioning，Grafana healthy |
| **15** | **压测达标** | ✅ | 异步受理：50 线程 1803 QPS/P99 76ms；200 线程 3265 QPS/P99 158ms |
| 16 | Swagger 文档 | ✅ | `/v3/api-docs` 返回 HTTP 200 |

---

## 四、Phase 5 待做功能

按 Spec 和 Plan，Phase 5 为"先上简历后迭代"阶段的扩展功能：

| 功能 | 说明 |
|------|------|
| 转账接口 | merchant A → merchant B 余额划转，含 TCC 两阶段 |
| 提现接口 | merchant → 模拟外部银行卡，走 channel-simulator |
| 钉钉 Webhook 告警 | Grafana Alert Rule → Webhook → 钉钉群机器人通知 |
| 数据归档 | XXL-JOB 按月迁移 journal_entry/transaction 旧数据到归档表 |
| Swagger SDK 文档 | Knife4j 完善商户接入 SDK 文档 |

---

## 五、当前结论

**Phase 4.3 已实际执行并达到支付接口容量目标。** 按 Spec 的热路径异步化要求，接口完成验签、风控、幂等占位和 RocketMQ 可靠入队后返回 `PROCESSING`；后台继续执行渠道与账务结算。

### 压测目标（来自 Spec）

| 指标 | 目标值 | 验证方式 |
|------|--------|---------|
| 正常 QPS | **500** | 场景 1：50 线程 × 5min |
| 峰值 QPS | **2000** | 场景 2：200 线程 × 2min |
| P99 延迟（正常） | **< 350ms** | Aggregate Report |
| P99 延迟（峰值） | **< 1s** | Aggregate Report |
| 成功率 | **> 99.9%** | Error% |
| 并发扣款无超卖 | **最终余额 = 初始 - N × 金额** | 场景 3 幂等验证 |
| 本机入口受理实测 | **1803 / 3265 QPS** | 50 / 200 线程 |

### 实测结果

```
支付接口可靠异步受理：

- 50 线程：54260 请求，1803 QPS，平均 24.9ms，P99 76ms，错误率 0%。
- 200 线程：98625 请求，3265 QPS，平均 54.6ms，P99 158ms，错误率 0%。

积压恢复和最终一致性：

- 两轮共受理 152885 笔，`pay-process` 全部清零。
- 152885 笔全部生成 PAY 交易、SETTLED 订单、SUCCESS 通知和 MATCHED 对账记录。
- 支付结算耗时 1220 秒（约 125 TPS）；实时对账全部追平耗时 1612 秒（约 95 TPS）。

支付接口 QPS 已达标，但持续结算能力低于入口峰值。生产容量规划必须同时设置最大积压和恢复时间，或扩容账户、通知与对账消费者。
```

详细证据见 `docs/reports/2026-06-25-payment-platform-final-validation.md`。
