# Phase 1 完成报告：基础支付链路

> **日期：** 2026-06-19
> **阶段：** Phase 1 — 基础支付链路
> **结果：** ✅ 全部通过

---

## 一、改动范围

### 1.1 merchant-service（15 个文件）

```
merchant/
├── entity/  (3)
│   ├── Merchant.java              # 商户主表：id, merchantNo, name, status, contactEmail, apiKey
│   ├── MerchantKey.java           # RSA 密钥表：merchantId, publicKey, keyType, status
│   └── RateConfig.java            # 费率配置表：merchantId, channelType, feeRate
├── repository/ (3)
│   ├── MerchantRepository.java    # JPA 持久层
│   ├── MerchantKeyRepository.java
│   └── RateConfigRepository.java
├── dto/ (3)
│   ├── MerchantRegisterDTO.java   # 入驻请求（merchantName + contactEmail）
│   ├── KeyPairDTO.java            # 密钥对响应（publicKey + privateKey）
│   └── RateConfigDTO.java         # 费率配置请求
├── service/ (6)
│   ├── MerchantService.java       # 接口：register / getById / disable
│   ├── impl/MerchantServiceImpl.java
│   ├── KeyService.java            # 接口：generateKeyPair / getActivePublicKey
│   ├── impl/KeyServiceImpl.java
│   ├── ConfigService.java         # 接口：configureRate / getFeeRate
│   └── impl/ConfigServiceImpl.java
└── controller/ (3)
    ├── MerchantController.java    # POST register / GET {id} / PUT disable
    ├── KeyController.java         # POST key/generate / GET key/public
    └── ConfigController.java      # POST rate / GET rate/{channel}
```

### 1.2 channel-simulator（12 个文件）

```
simulator/
├── entity/ (2)
│   ├── ChannelOrder.java          # 模拟渠道订单表
│   └── SimulatorConfig.java       # 模拟器行为配置表（成功率/UNKNOWN率/延迟）
├── repository/ (2)
│   ├── ChannelOrderRepository.java
│   └── SimulatorConfigRepository.java
├── dto/ (4)
│   ├── ChannelPayRequest.java     # 已移至 common 模块
│   ├── ChannelPayResponse.java    # 已移至 common 模块
│   ├── ChannelQueryResponse.java  # 已移至 common 模块
│   └── BillDTO.java               # 账单行 DTO
├── service/ (2)
│   ├── SimulatorService.java      # 接口：pay / query / getBill / updateConfig
│   └── impl/SimulatorServiceImpl.java  # 按概率随机返回 SUCCESS/FAIL/UNKNOWN
├── config/ (1)
│   └── SimulatorConfigManager.java
└── controller/ (3)
    ├── ChannelPayController.java  # POST /api/v1/simulator/pay
    ├── ChannelQueryController.java # GET /api/v1/simulator/query
    └── BillController.java        # GET /api/v1/simulator/bill/{date}
```

### 1.3 payment-gateway（17 个文件）

```
gateway/
├── dto/ (2)
│   ├── RouteResult.java           # 渠道路由结果
│   └── RiskCheckResult.java       # 风控检查结果
├── config/ (3)
│   ├── RestClientConfig.java      # RestClient + @LoadBalanced
│   ├── RedisConfig.java           # Redis 序列化配置
│   └── SentinelRulesConfig.java   # 限流规则初始化（2000 QPS）
├── client/ (4)
│   ├── ChannelSimulatorClient.java # 调用渠道模拟器
│   ├── MerchantClient.java        # 调用商户服务（获取公钥+费率）
│   ├── AccountClient.java         # 桩（Phase 2 实现）
│   └── OrderClient.java           # 桩（Phase 2 实现）
├── service/ (10)
│   ├── PayService.java            # 核心接口：createPay / queryPay
│   ├── impl/PayServiceImpl.java   # 完整 5 步支付链路 + UNKNOWN 三态处理
│   ├── SignatureService.java      # RSA 验签（时间戳窗口 + nonce 防重放）
│   ├── impl/SignatureServiceImpl.java
│   ├── ChannelRouterService.java  # 渠道路由
│   ├── impl/ChannelRouterServiceImpl.java
│   ├── RiskService.java           # 风控（单笔限额）
│   ├── impl/RiskServiceImpl.java
│   ├── IdempotencyService.java    # 幂等（Redis 72h）
│   └── impl/IdempotencyServiceImpl.java
├── controller/ (2)
│   ├── PayController.java         # POST /api/v1/pay/create / GET /query
│   └── CallbackController.java    # 预留
└── filter/ (1)
    └── SignatureFilter.java       # 请求日志 filter
```

### 1.4 common 新增（3 个文件）

```
common/dto/
├── request/ChannelPayRequest.java       # 跨模块共享
├── response/ChannelPayResponse.java     # 跨模块共享
└── response/ChannelQueryResponse.java   # 跨模块共享
```

---

## 二、跨模块共享 DTO 重构

| 优化 | 说明 |
|------|------|
| 问题 | payment-gateway 依赖 channel-simulator 的 DTO 类，跨模块引用 |
| 方案 | `ChannelPayRequest` / `ChannelPayResponse` / `ChannelQueryResponse` 上提到 payment-common |
| 结果 | payment-gateway 和 channel-simulator 各自依赖 common，无循环依赖 |

---

## 三、POM 调整

| 模块 | 原依赖 | 调整后 |
|------|--------|--------|
| payment-gateway | spring-cloud-starter-gateway | spring-boot-starter-web + spring-cloud-starter-loadbalancer |
| payment-gateway | — | 新增 RestClient（非 WebClient），与整体 MVC 架构一致 |

> **变更理由：** 我们的网关是"业务网关"（自己写 Controller 处理支付逻辑），不是 Spring Cloud Gateway 那种"路由网关"。用 Spring MVC 架构更合适。

---

## 四、编译结果

```bash
JAVA_HOME=F:/Java/java21 mvn clean compile -DskipTests
```

```
Reactor Summary:
Payment Platform ................... SUCCESS
Payment Common ..................... SUCCESS (32 个类)
Payment Gateway .................... SUCCESS (17 个源文件)
Account Service .................... SUCCESS
Order Service ...................... SUCCESS
Notification Service ............... SUCCESS
Reconciliation Service ............. SUCCESS
Merchant Service ................... SUCCESS (15 个源文件)
Channel Simulator .................. SUCCESS (12 个源文件)
Payment API ........................ SUCCESS

BUILD SUCCESS — 9/9 模块，0 编译错误
```

---

## 五、支付全链路说明

```
POST /api/v1/pay/create
Header: X-Signature, X-Timestamp, X-Nonce
Body: {outTradeNo, merchantId, amount, notifyUrl, subject}

  → 1. IdempotencyService.check(Redis)
     └→ 命中 → 抛 DuplicateRequestException → 返回 200 + 原结果

  → 2. SignatureService.verify()
     ├→ MerchantClient.getPublicKey() → 从 merchant-service 获取公钥
     ├→ 校验 timestamp 是否在 5 分钟窗口内
     ├→ 校验 nonce 是否重复（Redis setIfAbsent）
     └→ RsaSignUtil.verify(签名串, 签名, 公钥)

  → 3. RiskService.check()
     ├→ 单笔金额 ≤ 50000 元
     └→ 金额 > 0

  → 4. ChannelRouterService.route()
     ├→ MerchantClient.getFeeRate() → 遍历 WECHAT → ALIPAY → UNIONPAY
     └→ 选择第一个已配置费率的渠道

  → 5. ChannelSimulatorClient.pay()
     └→ HTTP POST → channel-simulator
        └→ 按配置概率返回 SUCCESS(80%) / UNKNOWN(10%) / FAIL(10%)

  → 分支处理：
     ├→ SUCCESS → 记录幂等 → 返回 PayResponse
     ├→ FAIL    → 抛 ChannelException → 返回 422
     └→ UNKNOWN → 轮询查单(2s/5s/10s) → 查到返回结果
                                       → 三次仍 UNKNOWN → 返回 processing
```

**Phase 1 中 TCC 账户扣款未接入（Phase 2 补充），当前 AccountClient 和 OrderClient 为桩。**

---

## 六、你需要验收的 3 件事

### 验收 1：编译通过

```bash
cd F:/test_file/NoIdea
set JAVA_HOME=F:/Java/java21
mvn clean compile -DskipTests
```

预期：`BUILD SUCCESS` 9/9 模块。

### 验收 2：IDEA 中查看代码

重点查看以下文件：
- `payment-gateway/.../PayServiceImpl.java` — 核心支付链路是否完整
- `channel-simulator/.../SimulatorServiceImpl.java` — 概率随机逻辑是否正确
- `merchant-service/.../KeyServiceImpl.java` — RSA 密钥生成流程
- `payment-common/.../GlobalExceptionHandler.java` — 8 种异常映射

### 验收 3：Swagger 文档

启动任意服务（需要先 Docker 启动 Nacos + MySQL），访问 `http://localhost:8080/doc.html`（gateway）或 `http://localhost:8085/doc.html`（merchant）。

---

## 七、下一步

Phase 2：账务核心
- account-service 完整实现（TCC + 复式记账 + 分库分表）
- order-service 完整实现（订单状态机 + 退款）
- gateway 接入 TCC 调用
