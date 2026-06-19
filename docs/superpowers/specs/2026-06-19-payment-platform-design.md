# 分布式支付收银台 & 账务系统 — 设计文档

## 项目定位

一个面向商户的支付收银台与账务系统，覆盖「充值 → 消费 → 退款」全生命周期。技术深度对标生产级金融系统，用于展示高并发、分布式事务、分库分表、消息队列等领域能力。

**简历命名：** 分布式支付收银台 & 账务系统（Payment Gateway & Accounting System）

## 技术选型总览

| 层次 | 组件 | 版本/说明 |
|------|------|-----------|
| 语言 | JDK 21 | 虚拟线程正式支持，Spring Boot 3.2+ 原生适配 |
| 框架 | Spring Boot 3.x + Spring Cloud | 微服务全家桶 |
| 注册+配置 | Nacos | 国内事实标准，注册配置一体 |
| 网关 | Spring Cloud Gateway | 响应式网关，整合 Sentinel |
| 内部 RPC | REST (OkHttp + WebClient) | 6 服务规模，REST 调试成本低于 gRPC |
| 限流熔断 | Sentinel | Dashboard 可视化，生产级规则配置 |
| 消息队列-业务 | RocketMQ | 事务消息 + 延迟消息（核心链路用） |
| 消息队列-数据 | Kafka | 高吞吐日志/埋点/对账数据流 |
| 分库分表 | ShardingSphere-JDBC | 应用层分片，轻量无 Proxy 运维成本 |
| 分布式事务 | TCC（核心）+ MQ 事务消息（外围） | 余额扣减用 TCC 强一致，通知/记录用最终一致 |
| 缓存 | Redis Cluster | 幂等 Token、渠道配置缓存、非余额热数据 |
| 本地缓存 | Caffeine | 渠道路由配置、费率表（低频变更数据） |
| 定时任务 | XXL-JOB | 对账调度、超时关单、日终清算 |
| 数据同步 | Canal | 监听 Binlog 触发实时对账 |
| 链路追踪 | SkyWalking | 全链路追踪，定位每个请求耗时瓶颈 |
| 指标采集 | Prometheus | 拉模式采集 QPS / 错误率 / JVM 指标 |
| 可视化面板 | Grafana | Dashboard 展示 + 告警规则配置 |
| 日志聚合 | Loki | Grafana 生态，轻量级，同面板看日志+指标 |
| 告警通知 | 钉钉/飞书 Webhook | 设计预留，Phase 5 实现（Grafana Alert → Webhook → 群机器人） |
| API 文档 | SpringDoc + Knife4j | OpenAPI 3.0，商户文档自动生成 |
| 分布式 ID | Snowflake (Hutool) | 本地生成，无网络开销 |
| 部署 | Docker Compose | 本地开发/演示一键启动 |

## 服务拆分（7 个组件）

| 服务 | 端口 | 职责 | 对外协议 |
|------|------|------|----------|
| payment-gateway | 8080 | 支付统一入口、签名验签、渠道路由、幂等、风控 | REST（商户 API） |
| account-service | 8081 | 账户管理、余额扣减/冻结、流水记录、TCC 实现 | REST（内部） |
| order-service | 8082 | 订单状态机、退款单、对账单生成 | REST（内部） |
| notification-service | 8083 | 商户 HTTP 回调、退避重试、站内消息 | REST（内部） |
| reconciliation-service | 8084 | Canal 实时对账、差异检测、XXL-JOB 调度 | REST（内部） |
| merchant-service | 8085 | 商户入驻、密钥管理、费率配置、渠道配置 | REST（管理后台） |
| channel-simulator | 8086 | 模拟微信/支付宝/银联支付接口 + 查单 + 账单下载 | REST（模拟渠道） |

**账务核心 : 支付网关 = 6 : 4**，account-service 和 order-service 是深度重点。

## 核心链路设计

### 链路 1：正常支付（TCC + 事务消息）

```
商户 POST /api/v1/pay（签名 + outTradeNo）
  → payment-gateway: 验签 → 幂等检查(Redis) → 频控 → 渠道路由
    → channel-simulator: 模拟扣款 → 返回 SUCCESS
      → account-service.TCC-Try(): 冻结商户余额
      → account-service.TCC-Confirm(): 余额实扣 + 生成复式流水(借:商户/贷:平台)
        → RocketMQ 事务消息(topic: pay-success)
          → order-service: 订单 PAID → SETTLED
          → notification-service: HTTP 回调商户(带退避重试)
            → Canal Binlog 触发 → Kafka(txn-log) → reconciliation-service 实时比对
```

### 链路 2：渠道返回 UNKNOWN（三态处理）

```
channel-simulator 超时 → 返回 UNKNOWN
  → payment-gateway: 不 Confirm 也不 Cancel
    → 轮询查单(payment-gateway 主动调 channel-simulator 查单接口)
      → 2s/5s/10s 三次
        → 查到 SUCCESS → 走正常 Confirm
        → 查到 FAIL → 走 Cancel + 释放冻结
        → 三次均 UNKNOWN → 写入异常订单表 → XXL-JOB 兜底对账修复
```

### 链路 3：退款（Saga 补偿）

```
商户请求退款 → order-service 校验原订单状态
  → 生成退款单(状态: REFUNDING)
    → account-service: 余额回退(平台→商户)
      → RocketMQ 事务消息(topic: refund-notify)
        → notification-service: 回调商户
```

### 链路 4：TCC 异常回滚

```
Try 成功(冻结=100) → Confirm 失败(DB 连接超时)
  → RocketMQ 回查机制重试 Confirm(最多 3 次)
    → 仍失败 → 超时 Cancel: 释放冻结金额 + 调用渠道冲正
```

## 消息队列设计

| Topic | MQ | 生产者 | 消费者 | 消息类型 | 说明 |
|-------|-----|--------|--------|----------|------|
| pay-success | RocketMQ | payment-gateway | order, notification | 事务消息 | 支付成功事件 |
| refund-notify | RocketMQ | order-service | notification, account | 事务消息 | 退款事件 |
| delay-close-order | RocketMQ | order-service | order-service | 延迟消息(30min) | 超时关单 |
| callback-retry | RocketMQ | notification | notification | 延迟消息(退避) | 回调重试 |
| txn-log | Kafka | account-service | reconciliation | 普通消息 | 对账数据流 |
| audit-event | Kafka | 所有服务 | 日志/ES | 普通消息 | 审计埋点 |

## 数据设计

### 账户模型：复式记账

每笔交易生成两条流水，借贷永远相等：

| 字段 | 类型 | 说明 |
|------|------|------|
| txn_id | BIGINT | Snowflake 生成 |
| debit_account_id | BIGINT | 借方账户 ID |
| credit_account_id | BIGINT | 贷方账户 ID |
| amount | DECIMAL(18,2) | 金额 |
| dr_cr_flag | CHAR(1) | D=借 / C=贷 |
| txn_type | VARCHAR(32) | PAY/REFUND/FREEZE/UNFREEZE |
| txn_time | DATETIME | 交易时间 |
| merchant_id | BIGINT | 分片键 |

### 分库分表策略

- **分片中间件：** ShardingSphere-JDBC
- **分片键：** `merchant_id`（90% 查询带商户维度，路由精准，避免广播查询）
- **分片算法：** 一致性哈希（4 库 × 8 表）
- **需要分片的表：** 账户表、流水表、订单表、退款单表
- **不分片的表：** 商户信息表、费率配置表、渠道配置表（广播表）

### 余额一致性保障

- **余额不缓存**，直接走分库分表 DB 读写
- **本地缓存(Caffeine)** 仅用于：渠道路由配置、费率表、商户信息（读多写极少数据）
- **Redis** 用于：幂等 Token、分布式锁、渠道配置热数据

## 安全设计

### API 签名（微信/支付宝同款方案）

```
商户请求流程：
1. 商户生成 RSA 密钥对，公钥上传到 merchant-service
2. 每次请求构建签名串：method + url + timestamp + nonce + body
3. 商户用私钥签名 → 放入 Header: X-Signature
4. 网关用商户公钥验签
5. timestamp 超出 5 分钟窗口 → 拒绝
6. nonce 写入 Redis(TTL=5min) → 已存在则拒绝（防重放）
```

### 幂等性

- 商户请求必须携带唯一 `outTradeNo`
- 网关 Redis key: `idem:{merchantId}:{outTradeNo}`，TTL 72h
- 命中 → 直接返回原结果（含退款场景）
- 未命中 → 放行 + 写入

## SLA & 容量设计

### 并发与峰值基线

| 指标 | 数值 |
|------|------|
| 注册商户 | 1000 |
| 日均活跃商户 | 200 |
| 正常 QPS（支付接口） | 500 |
| 峰值 QPS（大促 4 倍） | 2000 |
| 日均订单量 | 50 万笔 |
| 同时在线商户 | 50 |

> 该基线可在单机 16G + Docker Compose 环境下压测达标，有真实数据支撑。

### 热路径分析

| 优先级 | 接口 | 调用占比 | 优化策略 |
|--------|------|---------|----------|
| P0（核心） | POST /api/v1/pay 支付下单 | 60% | 异步化 + 连接池 + 虚拟线程 |
| P1 | GET /api/v1/pay/query 查询 | 25% | Redis 缓存订单状态(5min) |
| P2 | POST /api/v1/refund 退款 | 10% | 异步处理，先受理后通知 |
| P3 | GET /api/v1/account/balance | 5% | DB 分片精准路由，不缓存 |

**结论：** 支付下单是绝对热路径，优化资源集中在网关验签、渠道调用、TCC 三个环节。

### 数据规模

| 指标 | 数值 |
|------|------|
| 单笔交易数据量 | ~2KB（1 订单 + 2 流水 + 1 支付记录） |
| 日均数据增量 | ~1GB |
| 月增数据 | ~30GB |
| 年增数据 | ~360GB |
| 分片承载能力 | 4 库 × 8 表，单表 500 万行以下最优，可撑 3-5 年 |

### 响应时间 SLA（支付接口 < 1s）

| 环节 | 耗时预算 | 占比 | 备注 |
|------|---------|------|------|
| RSA 签名验签 | < 50ms | 5% | CPU 密集型，虚拟线程 |
| 幂等检查(Redis) | < 5ms | 0.5% | Redis GET |
| 渠道调用(模拟器) | < 100ms | 10% | 本地模拟 |
| TCC Try（冻结） | < 50ms | 5% | DB UPDATE |
| TCC Confirm（实扣） | < 50ms | 5% | DB UPDATE + 复式流水 |
| MQ 事务消息发送 | < 50ms | 5% | RocketMQ 半消息 |
| 网络/序列化 | < 45ms | 4.5% | REST + JSON |
| **同步链路合计** | **~350ms** | **35%** | 在 1s 内有 650ms 余量 |
| 异步链路（不阻塞） | — | — | 订单更新/回调/对账 |

### API 错误兜底策略

| 错误场景 | HTTP 状态码 | 错误码 | 兜底行为 |
|----------|------------|--------|---------|
| 签名验证失败 | 401 | SIGN_INVALID | 直接拒绝 + 告警日志 |
| 幂等重复请求 | 200 | DUPLICATE | 返回原结果（含退款），不报错 |
| 商户余额不足 | 422 | BALANCE_INSUFFICIENT | 拒绝 + 返回可用余额 |
| 渠道超时(UNKNOWN) | 202 | PROCESSING | 返回 processing + 后台轮询查单 |
| 渠道明确失败 | 422 | CHANNEL_FAIL | 返回原因 + 释放冻结 |
| TCC Confirm 失败 | — | — | RocketMQ 回查重试 3 次 + 告警 + 人工 |
| 系统内部错误 | 500 | INTERNAL_ERROR | 通用兜底，不暴露堆栈，返回 traceId |
| 商户不存在/停用 | 403 | MERCHANT_FORBIDDEN | 拒绝 + 返回原因 |

### 过载保护（四层防线）

**第一层：限流（Sentinel 令牌桶）**
- 商户级：单商户 100 QPS
- 接口级：支付接口总 2000 QPS
- IP 级：单 IP 50 QPS
- 超限 → HTTP 429 + "请稍后重试"
- 所有阈值配置在 Nacos，动态调整

**第二层：降级（Sentinel 熔断）**
- 慢调用比例 > 50% 持续 5s → 熔断 30s
- account-service 慢调用 > 30% → 关闭余额查询接口（降级为"稍后重试"）
- 支付接口不降级（核心链路），但排队等待
- 对账服务暂停实时对账，切换为仅批处理

**第三层：排队（MQ 削峰）**
- 支付请求堆积 → RocketMQ 天然缓冲
- 商户回调失败 → 延迟消息退避重试（1m/5m/15m/30m/1h）
- 超时关单走 delay-close-order（30min），不占实时资源

**第四层：缓存策略**
- 订单查询结果 → Redis 缓存 5 分钟（允许短暂不一致）
- 渠道配置/费率表 → Caffeine 本地缓存 1 分钟刷新
- 商户信息 → Redis 缓存，写时主动失效
- 余额 → **不缓存**，DB 分片直读（强一致要求）
- Redis 内存策略：allkeys-lru

## 可观测体系

```
请求 → SkyWalking 全链路追踪（每个环节耗时）
     → Prometheus 采集指标（QPS / 错误率 / JVM）
     → Grafana 面板展示（Dashboard 截图放简历）
     → Loki 聚合日志（统一看日志 + 指标 + 链路）
     → 指标异常 → Grafana Alert → 钉钉/飞书 Webhook（Phase 5 实现）
```

### Grafana Dashboard 核心面板

| 面板 | 内容 | 用途 |
|------|------|------|
| 网关总览 | QPS / P99 延迟 / 成功率 | 实时监控 |
| 支付链路 | 各环节耗时分布 | 瓶颈定位 |
| 账户服务 | TCC Try/Confirm 成功率 | 事务监控 |
| MQ 概览 | 消息堆积量 / 消费延迟 | 异步链路 |
| 对账面板 | 差异笔数 / 处理率 | 数据一致性 |

### 告警规则（设计预留，Phase 5 实现）

| 告警项 | 触发条件 | 通知渠道 |
|--------|---------|----------|
| 支付成功率 < 99.5% | 最近 5 分钟 | 钉钉群 |
| 接口 P99 > 1s | 持续 3 分钟 | 钉钉群 |
| 对账差异 > 0 笔 | 单次检查 | 钉钉 + @负责人 |
| MQ 堆积 > 1000 | 持续 5 分钟 | 钉钉群 |
| TCC Confirm 失败 | 任意 1 笔 | 钉钉 + 电话 |

## 对账体系（双层）

### 第一层：Canal 实时对账

```
交易流水写入 MySQL
  → Canal 监听 Binlog
    → 发送到 Kafka(txn-log)
      → reconciliation-service 消费
        → 与渠道侧(从 channel-simulator 账单接口拉取)比对
          → 一致 → Redis 标记已核对
          → 不一致 → 写入差异表(reconciliation_diff)
```

### 第二层：XXL-JOB 批处理对账

```
每日 02:00 调度
  → 从 channel-simulator 拉取 T-1 账单文件(模拟)
    → 逐笔比对内部流水
      → 生成对账报告(差异明细 + 金额汇总)
        → 差异自动冲销(小额) / 人工处理(大额)
```

## 渠道模拟器（channel-simulator）

独立的 Mock 服务，模拟真实支付渠道行为：

- **支付接口：** 返回 SUCCESS / FAIL / UNKNOWN（可配置概率）
- **查单接口：** 根据 outTradeNo 返回真实状态
- **账单接口：** 提供 T-1 日账单文件下载
- **异常模式：** 可配置延迟(2s/5s/10s)、超时、部分成功
- **用途：** 端到端测试 + 压测 + 异常演练

## 风控模块（网关内）

| 规则 | 维度 | 实现 |
|------|------|------|
| IP 频控 | 单 IP 每秒请求数 | Sentinel + Redis 滑动窗口 |
| 单笔限额 | 单笔交易金额上限 | 表达式引擎判断 |
| 日累计限额 | 单商户日交易总额 | Redis 计数器 + 每日重置 |
| 黑名单 | 商户/用户 | Redis Set |

## 监控与告警

| 指标 | 采集方式 | 告警阈值 |
|------|----------|----------|
| 支付成功率 | Prometheus | < 99.5% |
| 接口 P99 延迟 | SkyWalking | > 500ms |
| 对账差异笔数 | reconciliation-diff 表 | > 0 |
| TCC Confirm 失败 | 日志关键字 | 任意 1 笔 |
| MQ 消息堆积 | RocketMQ Dashboard | > 1000 条 |

## 项目结构

```
payment-platform/
├── payment-gateway/          # 支付网关服务
├── account-service/          # 账户服务（核心）
├── order-service/            # 订单服务
├── notification-service/     # 通知服务
├── reconciliation-service/   # 对账服务
├── merchant-service/         # 商户管理服务
├── channel-simulator/        # 渠道模拟器
├── payment-common/           # 公共模块(DTO、工具类、常量)
├── payment-api/              # 对外 SDK（商户接入文档）
├── docker-compose.yml        # 一键部署
├── docs/                     # 设计文档 + 压测报告
└── README.md
```

## 迭代路线

| 阶段 | 周期 | 内容 |
|------|------|------|
| **Phase 1** | 第 1-3 周 | 项目骨架 + 商户服务 + 支付网关 + 渠道模拟器 + 基础支付链路 |
| **Phase 2** | 第 4-6 周 | 账户服务 + TCC + 复式记账 + 分库分表 + 订单服务 |
| **Phase 3** | 第 7-8 周 | 对账服务 + Canal + XXL-JOB + 通知服务 + 回调重试 + Grafana Dashboard |
| **Phase 4** | 第 9-10 周 | 风控 + 安全加固 + Prometheus + Loki + 压测 + 性能调优 + 简历话术整理 |
| **Phase 5** | 第 11-12 周 | 转账 + 提现 + 告警通知（钉钉 Webhook） + 数据归档策略 |

## 预设身份：交易场景

- **平台方：** 系统运营方，拥有平台收入账户
- **商户方：** 入驻商家，拥有商户余额账户（向平台充值后用于业务付款）
- **用户方：** C 端消费者（通过商户页面完成付款，不直接在平台开户）
- **渠道方：** 模拟的微信/支付宝/银联等支付通道
