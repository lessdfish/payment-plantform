# 分布式支付收银台 & 账务系统 — 实现计划

> **Spec:** `docs/superpowers/specs/2026-06-19-payment-platform-design.md`
> **Target:** 单机 16G + Docker Compose，500 QPS 正常 / 2000 QPS 峰值
> **Base Package:** `com.payment.platform`

---

## Phase 0：项目骨架（第 1 周，最关键，一次搭好不返工）

### 目标

Maven 多模块项目可编译启动，Docker Compose 一键拉起所有中间件，公共模块包含全部共享代码。

---

### Task 0.1：Maven 父工程 POM

**文件:** `pom.xml`（项目根目录）

**关键依赖版本锁定（整个项目唯一版本声明处）：**

| 依赖 | 版本 | 说明 |
|------|------|------|
| Java | 21 | 虚拟线程 |
| Spring Boot | 3.3.5 | 稳定版，JDK 21 原生 |
| Spring Cloud | 2023.0.3 | 匹配 Boot 3.3.x |
| Spring Cloud Alibaba | 2023.0.1.0 | Nacos + Sentinel |
| ShardingSphere-JDBC | 5.5.1 | 分库分表 |
| RocketMQ Spring Boot Starter | 2.3.0 | 事务消息 |
| Spring Kafka | 内嵌 | 随 Boot 版本 |
| XXL-JOB | 2.4.1 | 定时任务 |
| Knife4j | 4.5.0 | Swagger UI |
| Hutool | 5.8.28 | 工具类（Snowflake 等） |
| MySQL Connector | 8.0.33 | 数据库驱动 |

**POM 结构：**
- `<parent>` spring-boot-starter-parent 3.3.5
- `<dependencyManagement>` 锁定上述所有版本
- `<modules>` 9 个子模块

---

### Task 0.2：公共模块 payment-common

**模块:** `payment-common/`

这个模块**零业务逻辑**，只有数据结构、工具类和异常定义，被所有服务模块依赖。

#### 包结构：`com.payment.platform.common`

```
com.payment.platform.common
├── constant
│   ├── ErrorCode.java          # 枚举：40+ 个错误码
│   ├── TxnTypeEnum.java        # 枚举：PAY / REFUND / FREEZE / UNFREEZE / TRANSFER
│   ├── OrderStatusEnum.java    # 枚举：CREATED / PAID / SETTLED / REFUNDING / REFUNDED / CLOSED
│   ├── DrCrFlagEnum.java       # 枚举：D(借) / C(贷)
│   ├── ChannelEnum.java        # 枚举：WECHAT / ALIPAY / UNIONPAY
│   └── PayResultEnum.java      # 枚举：SUCCESS / FAIL / UNKNOWN
├── dto
│   ├── request
│   │   ├── PayRequest.java         # outTradeNo + merchantId + amount + currency + notifyUrl + subject
│   │   ├── RefundRequest.java      # outRefundNo + originOutTradeNo + amount + reason
│   │   ├── PayQueryRequest.java    # outTradeNo + merchantId
│   │   └── MerchantRegisterRequest.java  # merchantName + contactEmail
│   ├── response
│   │   ├── PayResponse.java        # outTradeNo + payStatus + amount + channelOrderNo + paidTime
│   │   ├── RefundResponse.java     # outRefundNo + refundStatus + refundAmount
│   │   ├── PayQueryResponse.java   # outTradeNo + payStatus + amount
│   │   └── AccountBalanceResponse.java  # accountId + balance + frozenAmount + availableBalance
│   └── event
│       ├── PaySuccessEvent.java    # outTradeNo + merchantId + amount + channelOrderNo + paidTime
│       ├── RefundSuccessEvent.java # outRefundNo + originOutTradeNo + merchantId + refundAmount
│       └── TxnLogEvent.java        # txnId + debitAccountId + creditAccountId + amount + drCrFlag + txnType
├── exception
│   ├── BusinessException.java      # 统一业务异常(code + message + httpStatus)
│   ├── SignatureException.java     # 签名错误(401)
│   ├── DuplicateRequestException.java  # 幂等重复(200)
│   ├── BalanceInsufficientException.java  # 余额不足(422)
│   ├── ChannelException.java       # 渠道异常(422/202)
│   └── MerchantNotFoundException.java    # 商户不存在(403)
├── result
│   ├── ApiResult.java              # 统一响应: code + message + data + traceId + timestamp
│   └── PageResult.java             # 分页响应: records + total + pageNum + pageSize
└── util
    ├── SnowflakeIdGenerator.java   # Hutool Snowflake 封装，workerId 从 Nacos 获取
    ├── RsaSignUtil.java            # RSA 签名/验签工具
    └── NonceUtil.java              # 随机字符串生成 + Redis 去重辅助
```

**关键 POM 依赖：**
```xml
<dependencies>
    <dependency>spring-boot-starter-web</dependency>
    <dependency>spring-boot-starter-validation</dependency>
    <dependency>hutool-all</dependency>
    <dependency>lombok</dependency>
    <dependency>jackson-databind</dependency>
</dependencies>
```

---

### Task 0.3：Docker Compose 中间件编排

**文件:** `docker-compose.yml`

```yaml
services:
  nacos:
    image: nacos/nacos-server:v2.3.2
    ports: ["8848:8848", "9848:9848"]
    environment:
      MODE: standalone
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:8848/nacos/v1/console/health/readiness"]

  mysql-ds0:    # 分片数据源 0
    image: mysql:8.0.33
    ports: ["3306:3306"]
    environment:
      MYSQL_ROOT_PASSWORD: root123
    volumes:
      - ./sql/init-ds0.sql:/docker-entrypoint-initdb.d/init.sql

  mysql-ds1:    # 分片数据源 1
    image: mysql:8.0.33
    ports: ["3307:3306"]

  mysql-ds2:    # 分片数据源 2
    image: mysql:8.0.33
    ports: ["3308:3306"]

  mysql-ds3:    # 分片数据源 3
    image: mysql:8.0.33
    ports: ["3309:3306"]

  redis:
    image: redis:7.2
    ports: ["6379:6379"]

  rocketmq-namesrv:
    image: apache/rocketmq:5.2.0
    command: sh mqnamesrv
    ports: ["9876:9876"]

  rocketmq-broker:
    image: apache/rocketmq:5.2.0
    command: sh mqbroker -n rocketmq-namesrv:9876
    ports: ["10911:10911", "10909:10909"]

  kafka:
    image: bitnami/kafka:3.7.0
    ports: ["9092:9092"]

  canal-server:
    image: canal/canal-server:v1.1.7
    ports: ["11111:11111"]

  xxl-job-admin:
    image: xuxueli/xxl-job-admin:2.4.1
    ports: ["8087:8080"]
    environment:
      PARAMS: "--spring.datasource.url=jdbc:mysql://mysql-ds0:3306/xxl_job"

  sentinel-dashboard:
    image: bladex/sentinel-dashboard:1.8.7
    ports: ["8858:8080"]
```

---

### Task 0.4：每个服务模块的 Spring Boot 骨架

**全部 7 个模块统一模板：**

每个模块包含：
```
<module-name>/
├── pom.xml                          # 依赖 payment-common + 自己的特殊依赖
└── src/main/
    ├── java/com/payment/platform/<name>/
    │   ├── <Name>Application.java       # @SpringBootApplication
    │   └── config/
    │       └── <Name>Config.java        # 模块特殊配置（如 Swagger）
    └── resources/
        ├── application.yml              # 服务名 + 端口 + Nacos 地址
        └── bootstrap.yml                # Nacos 配置导入
```

**bootstrap.yml 模板（所有服务通用，仅改 service name 和 port）：**
```yaml
spring:
  application:
    name: payment-gateway
  cloud:
    nacos:
      discovery:
        server-addr: localhost:8848
      config:
        server-addr: localhost:8848
        file-extension: yaml
  profiles:
    active: dev

server:
  port: 8080
```

**7 个模块的 POM 依赖矩阵：**

| 模块 | 额外依赖 |
|------|---------|
| payment-gateway | spring-cloud-starter-gateway, sentinel-spring-cloud-gateway-adapter, rocketmq-spring-boot-starter |
| account-service | shardingsphere-jdbc, rocketmq-spring-boot-starter, spring-kafka |
| order-service | shardingsphere-jdbc, rocketmq-spring-boot-starter |
| notification-service | rocketmq-spring-boot-starter |
| reconciliation-service | xxl-job-core, spring-kafka, canal-client |
| merchant-service | (仅 payment-common) |
| channel-simulator | (仅 payment-common) |

---

### Task 0.5：统一异常处理 & 响应拦截器

**文件:** `payment-common/src/main/java/com/payment/platform/common/result/ApiResult.java`

```java
/**
 * 统一 API 响应体。
 * <p>所有 Controller 返回值统一包装为此类型，由 GlobalExceptionHandler 和 ResponseBodyAdvice 协同处理。</p>
 *
 * @param <T> 响应数据类型
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ApiResult<T> {
    /** 业务状态码，0 表示成功，非 0 表示错误 */
    private int code;

    /** 提示信息，成功时为 "success"，失败时为具体错误描述 */
    private String message;

    /** 响应数据，失败时为 null */
    private T data;

    /** 链路追踪 ID，对应 SkyWalking traceId，方便定位问题 */
    private String traceId;

    /** 响应时间戳（毫秒） */
    private long timestamp;

    public static <T> ApiResult<T> success(T data) {
        return new ApiResult<>(0, "success", data,
                MDC.get("traceId"), System.currentTimeMillis());
    }

    public static <T> ApiResult<T> fail(ErrorCode errorCode) {
        return new ApiResult<>(errorCode.getCode(), errorCode.getMessage(), null,
                MDC.get("traceId"), System.currentTimeMillis());
    }
}
```

**文件:** `payment-common/src/main/java/com/payment/platform/common/exception/GlobalExceptionHandler.java`

这是一个 `@RestControllerAdvice`，统一拦截所有异常，返回对应的 HTTP 状态码和错误码。

**拦截映射：**
- `SignatureException` → 401 SIGN_INVALID
- `DuplicateRequestException` → 200 DUPLICATE
- `BalanceInsufficientException` → 422 BALANCE_INSUFFICIENT
- `ChannelException` → 202 PROCESSING / 422 CHANNEL_FAIL
- `MerchantNotFoundException` → 403 MERCHANT_FORBIDDEN
- `MethodArgumentNotValidException` → 400 PARAM_INVALID
- `Exception` → 500 INTERNAL_ERROR

> GlobalExceptionHandler 放在 payment-common 中，各服务通过 `scanBasePackages = "com.payment.platform"` 将其纳入 Spring 扫描范围。

---

### Task 0.6：验证骨架

| 验证项 | 具体操作 |
|--------|---------|
| 依赖冲突检查 | `mvn dependency:tree -Dverbose | grep "conflict"` 无输出 |
| 编译通过 | `mvn clean compile -DskipTests` 全部成功 |
| Nacos 注册 | 启动任意服务 → Nacos 控制台可见 |
| 中间件全部启动 | `docker-compose ps` 13 个容器全部 healthy |

**Phase 0 完成标志：7 个空服务全部启动并注册到 Nacos，无任何依赖冲突。**

---

## Phase 1：基础支付链路（第 1-3 周）

### 目标

商户注册 + 支付网关 + 渠道模拟器打通，一笔支付从商户请求到渠道返回完整跑通。

---

### 1.1 merchant-service

**包：** `com.payment.platform.merchant`

```
merchant/
├── controller
│   ├── MerchantController.java     # 商户入驻/查询/停用
│   ├── KeyController.java          # RSA 密钥对管理
│   └── ConfigController.java       # 费率/渠道配置
├── service
│   ├── MerchantService.java        # 接口
│   ├── impl/MerchantServiceImpl.java
│   ├── KeyService.java             # 接口：生成密钥对、存储公钥
│   ├── impl/KeyServiceImpl.java
│   ├── ConfigService.java          # 接口：费率 CRUD
│   └── impl/ConfigServiceImpl.java
├── entity
│   ├── Merchant.java               # id, merchantNo, name, status, contactEmail, apiKey, createTime, updateTime
│   ├── MerchantKey.java            # id, merchantId, publicKey, keyType(RSA), status, createTime
│   └── RateConfig.java             # id, merchantId, channelType, feeRate(DECIMAL), status
├── repository
│   ├── MerchantRepository.java     # extends JpaRepository<Merchant, Long>
│   ├── MerchantKeyRepository.java
│   └── RateConfigRepository.java
└── dto
    ├── MerchantRegisterDTO.java
    ├── KeyPairDTO.java             # privateKey + publicKey（返回给商户的密钥对）
    └── RateConfigDTO.java
```

**REST API：**

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | /api/v1/merchant/register | 商户入驻 |
| GET | /api/v1/merchant/{merchantId} | 查询商户信息 |
| PUT | /api/v1/merchant/{merchantId}/disable | 停用商户 |
| POST | /api/v1/merchant/{merchantId}/key/generate | 生成 RSA 密钥对 |
| GET | /api/v1/merchant/{merchantId}/key/public | 获取商户公钥（内部调用） |
| POST | /api/v1/merchant/{merchantId}/rate | 配置费率 |
| GET | /api/v1/merchant/{merchantId}/rate/{channel} | 查询费率（内部调用） |

---

### 1.2 channel-simulator

**包：** `com.payment.platform.simulator`

```
simulator/
├── controller
│   ├── ChannelPayController.java   # 模拟支付接口
│   ├── ChannelQueryController.java # 模拟查单接口
│   └── BillController.java         # 模拟账单下载接口
├── service
│   ├── SimulatorService.java       # 接口
│   └── impl/SimulatorServiceImpl.java   # 按配置概率返回 SUCCESS/FAIL/UNKNOWN
├── dto
│   ├── ChannelPayRequest.java      # 商户请求参数（字段同真实渠道）
│   ├── ChannelPayResponse.java     # 支付结果 + channelOrderNo
│   ├── ChannelQueryResponse.java   # 查单结果
│   └── BillDTO.java                # 账单行
├── entity
│   ├── ChannelOrder.java           # id, channelOrderNo, outTradeNo, amount, status, createTime
│   └── SimulatorConfig.java        # id, delayMs, successRate(0-1), unknownRate(0-1)
└── config
    └── SimulatorConfigManager.java # 运行时动态调整模拟器行为的配置管理器
```

**REST API：**

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | /api/v1/simulator/pay | 模拟发起支付（可配置延迟/概率） |
| POST | /api/v1/simulator/query | 模拟查单 |
| GET | /api/v1/simulator/bill/{date} | 模拟 T-1 日账单下载 |
| PUT | /api/v1/simulator/config | 动态修改模拟行为（delay/成功率/UNKNOWN率） |

**关键设计：**
- 支付接口按 `SimulatorConfig.successRate` 概率返回 SUCCESS，`unknownRate` 概率返回 UNKNOWN，其余返回 FAIL
- 每次调用记录到 `ChannelOrder` 表，查单接口从该表查询
- 账单接口根据日期范围从 `ChannelOrder` 表聚合生成

---

### 1.3 payment-gateway

**包：** `com.payment.platform.gateway`

```
gateway/
├── controller
│   ├── PayController.java          # 支付下单/查询接口
│   └── CallbackController.java     # 渠道异步回调接收（预留）
├── service
│   ├── PayService.java             # 接口：支付下单核心逻辑
│   ├── impl/PayServiceImpl.java
│   ├── SignatureService.java       # 接口：RSA 验签/签名
│   ├── impl/SignatureServiceImpl.java
│   ├── ChannelRouterService.java   # 接口：渠道路由选择
│   ├── impl/ChannelRouterServiceImpl.java
│   ├── RiskService.java            # 接口：风控检查
│   ├── impl/RiskServiceImpl.java
│   └── IdempotencyService.java     # 接口：幂等性校验
│       └── impl/IdempotencyServiceImpl.java
├── client
│   ├── AccountClient.java          # 通过 RestClient 调用 account-service
│   ├── OrderClient.java            # 通过 RestClient 调用 order-service
│   ├── MerchantClient.java         # 通过 RestClient 调用 merchant-service
│   └── ChannelSimulatorClient.java # 通过 RestClient 调用 channel-simulator
├── config
│   ├── RestClientConfig.java       # RestClient Bean + 连接池配置
│   ├── SentinelRulesConfig.java    # Sentinel 限流/熔断规则初始化
│   └── RedisConfig.java            # Redis 序列化 + 连接池
├── filter
│   └── SignatureFilter.java        # 全局过滤器：对所有 /api/v1/pay/** 验签
└── dto
    ├── RouteResult.java            # 渠道路由结果(channelType + channelUrl + feeRate)
    └── RiskCheckResult.java        # 风控结果(pass + rejectReason)
```

**核心类详细设计：**

**PayService.java（核心接口）：**
```java
/**
 * 支付服务接口，处理支付下单和查询的核心业务逻辑。
 */
public interface PayService {

    /**
     * 商户支付下单，同步返回支付结果。
     * <p>处理流程：幂等检查 → 验签 → 风控 → 路由 → 渠道扣款 → TCC 扣账。</p>
     *
     * @param request   支付请求参数
     * @param signature RSA 签名（Base64）
     * @param timestamp 请求时间戳（秒级）
     * @param nonce     随机字符串（防重放）
     * @return 支付结果
     */
    PayResponse createPay(PayRequest request, String signature,
                          String timestamp, String nonce);

    /**
     * 根据商户订单号查询支付状态。
     *
     * @param outTradeNo 商户订单号
     * @param merchantId 商户 ID
     * @return 支付查询结果
     */
    PayQueryResponse queryPay(String outTradeNo, Long merchantId);
}
```

**PayServiceImpl 核心流程（链路 1 的完整实现）：**
```
createPay():
  1. 幂等检查 → IdempotencyService.check(merchantId, outTradeNo)
     → 命中则直接返回缓存结果
  2. 验签 → SignatureService.verify(signature, merchantId, request)
  3. 风控检查 → RiskService.check(merchantId, request)
  4. 渠道路由 → ChannelRouterService.route(merchantId, amount)
  5. 调用渠道 → ChannelSimulatorClient.pay(routeResult, request)
     → SUCCESS: 继续步骤 6
     → FAIL: 返回错误
     → UNKNOWN: 标记 processing + 后台查单
  6. TCC Try → AccountClient.tryFreeze(merchantId, amount, outTradeNo)
  7. TCC Confirm → AccountClient.confirm(merchantId, amount, outTradeNo)
  8. 发送事务消息 → RocketMQTemplate.sendMessageInTransaction("pay-success", event)
  9. 记录幂等结果 → IdempotencyService.save(merchantId, outTradeNo, response)
  10. 返回 PayResponse
```

**AccountClient.java（REST 客户端）：**
```java
/**
 * 账户服务 REST 客户端，封装对 account-service 的 HTTP 调用。
 * <p>使用 RestClient + 虚拟线程实现高性能内部调用。</p>
 */
@Component
public class AccountClient {

    /**
     * TCC Try 阶段：冻结商户余额。
     * <p>调用 account-service 的 TCC Try 接口，预扣指定金额。</p>
     *
     * @param merchantId  商户 ID
     * @param amount      冻结金额
     * @param bizOrderNo  业务单号（幂等键）
     * @return Try 结果，包含 tccId
     */
    public TryResult tryFreeze(Long merchantId, BigDecimal amount, String bizOrderNo) { ... }

    /**
     * TCC Confirm 阶段：确认扣减冻结金额。
     *
     * @param tccId TCC 事务 ID（由 Try 阶段返回）
     * @return Confirm 结果
     */
    public ConfirmResult confirm(String tccId) { ... }

    /**
     * TCC Cancel 阶段：释放冻结金额。
     *
     * @param tccId TCC 事务 ID
     * @return Cancel 结果
     */
    public CancelResult cancel(String tccId) { ... }

    /**
     * 查询商户账户余额（含冻结金额和可用余额）。
     *
     * @param merchantId 商户 ID
     * @return 账户余额信息
     */
    public AccountBalanceResponse getBalance(Long merchantId) { ... }
}
```

**REST API：**

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | /api/v1/pay/create | 商户支付下单（需验签） |
| GET | /api/v1/pay/query | 商户支付查询 |

---

### 1.4 Phase 1 验证

| 验证项 | 操作 | 预期 |
|--------|------|------|
| 商户注册 | POST /api/v1/merchant/register | 返回 merchantId + apiKey |
| 生成密钥 | POST /api/v1/merchant/{id}/key/generate | 返回公钥 + 私钥 |
| 支付请求 | POST /api/v1/pay/create（带签名） | 返回 outTradeNo + SUCCESS |
| 幂等验证 | 相同 outTradeNo 再次请求 | 返回 DUPLICATE + 原结果 |
| 渠道查询 | GET /api/v1/pay/query | 返回订单状态 |
| Swagger | 访问 /doc.html | Knife4j 页面可见 |

---

## Phase 2：账务核心（第 4-6 周）

### 目标

账户服务 + 分库分表 + TCC + 复式记账 + 订单服务，核心账务链路闭环。

---

### 2.1 account-service（最核心模块）

**包：** `com.payment.platform.account`

```
account/
├── controller
│   ├── AccountController.java      # 余额查询（内部）
│   └── TccController.java          # TCC Try/Confirm/Cancel（内部）
├── service
│   ├── AccountService.java         # 接口
│   ├── impl/AccountServiceImpl.java
│   ├── TccService.java             # 接口：TCC 三阶段
│   ├── impl/TccServiceImpl.java
│   ├── JournalService.java         # 接口：复式记账流水
│   └── impl/JournalServiceImpl.java
├── entity
│   ├── Account.java                # id, merchantId, balance(total), frozenAmount, version(乐观锁), createTime, updateTime
│   ├── Transaction.java            # id, txnId, merchantId, amount, txnType, outTradeNo, status, createTime
│   └── JournalEntry.java           # id, txnId, debitAccountId, creditAccountId, amount, drCrFlag, txnType, txnTime, merchantId
├── repository
│   ├── AccountRepository.java
│   ├── TransactionRepository.java
│   └── JournalEntryRepository.java
├── config
│   ├── ShardingConfig.java         # ShardingSphere 分库分表规则
│   └── SnowflakeConfig.java        # Snowflake workerId 配置
├── producer
│   └── TxnLogProducer.java         # 发送交易流水到 Kafka(txn-log)
└── dto
    ├── TryRequest.java             # merchantId + amount + bizOrderNo
    ├── TryResponse.java            # tccId + frozen
    ├── ConfirmRequest.java         # tccId
    ├── CancelRequest.java          # tccId
    └── JournalDTO.java
```

**TccService.java（核心接口）：**
```java
/**
 * TCC 分布式事务服务接口。
 * <p>实现 Try-Confirm-Cancel 三阶段协议，保证账户余额操作的最终一致性。</p>
 * <p>Try 阶段预扣（冻结），Confirm 阶段实扣，Cancel 阶段释放冻结。</p>
 */
public interface TccService {

    /**
     * Try 阶段：冻结商户余额。
     * <p>通过乐观锁 + 余额充足性校验防止超卖。
     * SQL: UPDATE account SET frozen_amount = frozen_amount + ? WHERE merchant_id = ?
     *      AND balance - frozen_amount >= ? AND version = ?</p>
     *
     * @param request 包含商户 ID、金额、业务单号
     * @return tccId + 冻结确认信息
     * @throws BalanceInsufficientException 可用余额不足
     */
    TryResponse tryFreeze(TryRequest request);

    /**
     * Confirm 阶段：实扣余额，生成复式记账流水。
     * <p>必须幂等：同一 tccId 重复调用直接返回成功，不重复扣款。
     * 内部生成两笔流水（借方 + 贷方），保证借贷平衡。</p>
     *
     * @param request 包含 tccId
     */
    void confirm(ConfirmRequest request);

    /**
     * Cancel 阶段：释放冻结金额。
     * <p>必须幂等：同一 tccId 重复调用直接返回成功，不重复释放。</p>
     *
     * @param request 包含 tccId
     */
    void cancel(CancelRequest request);
}
```

**TCC 实现关键点：**
- Try 用 `UPDATE ... WHERE balance - frozen_amount >= ?` 防止超卖
- `version` 字段做乐观锁，`UPDATE ... WHERE version = ?`，失败即并发冲突，抛出重试
- Confirm/Cancel 必须**幂等**：通过 tccId 在 `Transaction` 表查重，已处理直接返回
- Confirm 失败 → 不立即 Cancel，而是依赖 RocketMQ 回查 + 重试（见链路 4 设计）

**ShardingConfig.java：**
```
- 4 个数据源：ds0(3306), ds1(3307), ds2(3308), ds3(3309)
- 分片键：merchant_id
- 分片算法：INLINE: ds$->{merchant_id % 4}
- 表分片：account_$->{0..7}, transaction_$->{0..7}, journal_entry_$->{0..7}
- 表分片算法：INLINE: $->{merchant_id % 8}
- 广播表（不分片）：无（商户信息在 merchant-service 的独立 DB）
```

**AccountService.java：**
```java
/**
 * 账户服务接口，处理账户查询和充值。
 */
public interface AccountService {

    /**
     * 查询商户账户余额（含冻结金额和可用余额）。
     *
     * @param merchantId 商户 ID
     * @return 账户余额信息（balance / frozenAmount / availableBalance）
     */
    AccountBalanceResponse getBalance(Long merchantId);

    /**
     * 商户充值：平台向商户账户转入金额。
     * <p>单边操作，不涉及 TCC。生成一条贷记流水。</p>
     *
     * @param merchantId  商户 ID
     * @param amount      充值金额
     * @param outTradeNo  外部充值单号（幂等键）
     */
    void recharge(Long merchantId, BigDecimal amount, String outTradeNo);
}
```

---

### 2.2 order-service

**包：** `com.payment.platform.order`

```
order/
├── controller
│   ├── OrderController.java        # 订单查询（内部）
│   └── RefundController.java       # 退款申请
├── service
│   ├── OrderService.java           # 接口
│   ├── impl/OrderServiceImpl.java
│   ├── RefundService.java          # 接口
│   └── impl/RefundServiceImpl.java
├── entity
│   ├── Order.java                  # id, orderNo(Snowflake), outTradeNo, merchantId, amount, status(OrderStatusEnum), channelOrderNo, createTime, updateTime
│   └── RefundOrder.java            # id, refundNo, originOrderNo, merchantId, refundAmount, status(REFUNDING/REFUNDED/FAILED), createTime
├── repository
│   ├── OrderRepository.java
│   └── RefundOrderRepository.java
├── consumer
│   ├── PaySuccessConsumer.java     # 消费 pay-success 消息
│   └── RefundNotifyConsumer.java   # 消费 refund-notify 消息
├── producer
│   └── RefundProducer.java         # 发送 refund-notify 事务消息
└── config
    └── ShardingConfig.java         # 按 merchant_id 分片
```

**OrderService.java：**
```java
/**
 * 订单服务接口。
 */
public interface OrderService {

    /**
     * 根据商户订单号查询订单。
     * @param outTradeNo 商户订单号
     * @param merchantId 商户 ID（分片路由必需）
     */
    Order getByOutTradeNo(String outTradeNo, Long merchantId);

    /**
     * 根据内部订单号查询订单。
     */
    Order getByOrderNo(String orderNo);

    /**
     * 更新订单状态。
     * @param orderNo   内部订单号
     * @param newStatus 新状态
     */
    void updateStatus(String orderNo, OrderStatusEnum newStatus);
}
```

**PaySuccessConsumer.java：**
```java
/**
 * 消费支付成功事件，创建并更新订单状态。
 * <p>消费 RocketMQ pay-success topic 的事务消息。</p>
 */
@RocketMQMessageListener(topic = "pay-success",
        consumerGroup = "order-pay-success-consumer")
public class PaySuccessConsumer implements RocketMQListener<PaySuccessEvent> {

    /**
     * 处理支付成功事件：创建订单（PAID）→ 结算（SETTLED）。
     * <p>消息消费失败时 RocketMQ 会自动重试，保证最终处理成功。</p>
     */
    @Override
    public void onMessage(PaySuccessEvent event) {
        // 1. 创建 Order（status=PAID）
        // 2. 更新为 SETTLED
        // 3. 如需要，自动确认收货/结算
    }
}
```

---

### 2.3 Phase 2 验证

| 验证项 | 操作 | 预期 |
|--------|------|------|
| 分库分表正确 | 用不同 merchantId 创建数据 | 路由到不同库 |
| TCC Try | 调用 Try 接口 | 余额未减，冻结增加 |
| TCC Confirm | 调用 Confirm 接口 | 余额实扣，生成 2 条复式流水 |
| TCC Cancel | Try 后调 Cancel | 冻结释放，余额不变 |
| 并发扣款 | JMeter 50 线程同时扣同一商户 | 最终余额 = 初始 - 50 × 金额（无超卖） |
| MQ 消费 | 发送 pay-success 消息 | order-service 消费并创建订单 |
| 退款链路 | 退款请求 → 退款单创建 → MQ 通知 | 全链路状态正确 |

---

## Phase 3：对账 & 通知（第 7-8 周）

### 目标

对账服务 + Canal + XXL-JOB + 通知服务 + Grafana 面板。

---

### 3.1 reconciliation-service

**包：** `com.payment.platform.reconciliation`

```
reconciliation/
├── service
│   ├── ReconciliationService.java  # 接口
│   ├── impl/ReconciliationServiceImpl.java
│   ├── DiffService.java            # 接口：差异处理
│   └── impl/DiffServiceImpl.java
├── consumer
│   └── TxnLogConsumer.java         # 消费 Kafka(txn-log)，实时对账
├── job
│   ├── DailyReconciliationJob.java # XXL-JOB：每日批处理对账
│   └── SettlementJob.java          # XXL-JOB：日终清算
├── entity
│   └── ReconciliationDiff.java     # id, outTradeNo, merchantId, internalAmount, channelAmount, diffAmount, status, createTime
├── repository
│   └── ReconciliationDiffRepository.java
├── client
│   └── ChannelBillClient.java      # 调用 channel-simulator 的账单接口
└── config
    ├── CanalConfig.java             # Canal 连接配置
    └── XxlJobConfig.java            # XXL-JOB 执行器注册
```

**TxnLogConsumer.java（Canal 实时对账）：**
```java
/**
 * 消费 Canal 推送的 Binlog 变更事件，执行实时对账。
 * <p>当交易流水写入 DB 时，Canal 将 Binlog 转化为 Kafka 消息，
 * 本消费者接收后立即与渠道侧比对，实现秒级差异发现。</p>
 */
@KafkaListener(topics = "txn-log", groupId = "reconciliation-txn-log")
public class TxnLogConsumer {

    /**
     * 处理单条交易流水变更事件。
     * <p>流程：解析流水 → 调渠道查单 → 比对金额/状态 → 写入差异表。</p>
     */
    @KafkaHandler
    public void onMessage(TxnLogEvent event) {
        // 1. 解析 JournalEntry 数据
        // 2. 调 ChannelBillClient.query(outTradeNo) 查渠道侧状态
        // 3. 比对金额和状态
        // 4. 一致 → Redis SET reconciled:{outTradeNo} = true
        // 5. 不一致 → INSERT ReconciliationDiff
    }
}
```

**DailyReconciliationJob.java：**
```java
/**
 * 每日批处理对账任务（XXL-JOB 调度）。
 * <p>每日凌晨 2:00 执行，拉取渠道 T-1 日账单与内部流水逐笔比对。</p>
 */
@Component
public class DailyReconciliationJob {

    /**
     * 日终对账主流程。
     * <p>从渠道模拟器下载账单 → 读取内部流水 → 逐笔比对 →
     * 小额差异自动冲销，大额差异标记人工处理。</p>
     */
    @XxlJob("dailyReconciliationJob")
    public void execute() throws Exception {
        // 1. 从 ChannelBillClient.download(date) 下载 T-1 账单
        // 2. 逐笔读取账单
        // 3. 查 JournalEntry 表比对
        // 4. 生成对账报告
        // 5. 小额差异自动冲销（< 1 元），大额标记待人工处理
    }
}
```

---

### 3.2 notification-service

**包：** `com.payment.platform.notification`

```
notification/
├── controller
│   └── NotificationController.java # 查询通知记录（内部）
├── service
│   ├── NotifyService.java          # 接口
│   ├── impl/NotifyServiceImpl.java
│   ├── RetryService.java           # 接口：退避重试
│   └── impl/RetryServiceImpl.java
├── consumer
│   ├── PayNotifyConsumer.java      # 消费 pay-success → 回调商户
│   └── RefundNotifyConsumer.java   # 消费 refund-notify → 回调商户
├── entity
│   └── NotifyRecord.java           # id, merchantId, outTradeNo, notifyUrl, body, status(PENDING/SUCCESS/FAILED), retryCount, nextRetryTime, createTime
├── repository
│   └── NotifyRecordRepository.java
└── config
    └── RetryConfig.java            # 退避间隔配置
```

**PayNotifyConsumer.java：**
```java
/**
 * 消费支付成功事件，向商户发送 HTTP 回调通知。
 * <p>消费失败时通过延迟消息实现退避重试，最多重试 5 次。</p>
 */
@RocketMQMessageListener(topic = "pay-success",
        consumerGroup = "notify-pay-success-consumer")
public class PayNotifyConsumer implements RocketMQListener<PaySuccessEvent> {

    /**
     * 处理支付成功事件：构造签名回调请求 → POST 到商户 notifyUrl。
     * <p>成功则更新通知记录为 SUCCESS，失败则发送延迟重试消息。</p>
     */
    @Override
    public void onMessage(PaySuccessEvent event) {
        // 1. 构造回调请求体（签名 + event data）
        // 2. POST 到商户 notifyUrl（超时 5s）
        // 3. 成功 → 更新 NotifyRecord.status = SUCCESS
        // 4. 失败 → 发送延迟消息到 callback-retry topic
        // 5. 超过最大重试次数(5次) → 标记 FAILED + 告警日志
    }
}
```

**退避重试策略：** 1min → 5min → 15min → 30min → 1h，通过 RocketMQ 延迟消息级别实现。

---

### 3.3 Phase 3 验证

| 验证项 | 操作 | 预期 |
|--------|------|------|
| Canal 消费 | 插入 JournalEntry → 检查 Kafka 是否有消息 | txn-log topic 有数据 |
| 实时对账 | Canal 触发 → 比对渠道 | 一致则 Redis 标记，不一致则写 diff |
| 批处理对账 | 手动触发 XXL-JOB | 生成对账报告 |
| 商户回调 | 支付成功 → 检查 NotifyRecord | 状态 SUCCESS |
| 回调重试 | 模拟商户返回 500 → 检查重试 | 1min/5min 递增重试 |
| Grafana | 访问面板 | 网关 QPS/P99 显示 |

---

## Phase 4：生产加固（第 9-10 周）

### 4.1 风控模块（网关内扩展）

在 payment-gateway 的 `RiskService` 中实现：

| 规则 | 实现 | 配置来源 |
|------|------|---------|
| IP 频控 | Sentinel `FlowRule` + 商户 IP | Nacos 动态配置 |
| 单笔限额 | `RiskService.checkAmount(merchantId, amount)` | merchant-service 费率表 |
| 日累计限额 | Redis `INCR daily:amount:{merchantId}` + TTL 到次日 0 点 | 默认 100 万/天 |
| 黑名单 | Redis `SISMEMBER blacklist:merchant {merchantId}` | 手动维护 |

### 4.2 Prometheus + Loki + Grafana 完整接入

**配置项：**

| 服务 | 添加内容 |
|------|---------|
| 所有 Java 服务 | `micrometer-registry-prometheus` 依赖 |
| 所有 Java 服务 | `application.yml` 加 `management.endpoints.web.exposure.include: prometheus,health,metrics` |
| Docker | `prometheus.yml` 配置文件 + Prometheus 容器 |
| Docker | Loki + Promtail 容器（日志采集） |
| Grafana | 5 个 Dashboard JSON（导入） |

### 4.3 压测

**工具：** JMeter 或 wrk

**压测场景：**

| 场景 | 并发 | 持续时间 | 预期 |
|------|------|---------|------|
| 正常支付 | 50 线程 | 5 分钟 | P99 < 350ms, 成功率 > 99.9% |
| 峰值支付 | 200 线程 | 2 分钟 | P99 < 1s, 无超卖 |
| 幂等压测 | 重复同一 outTradeNo | 100 线程 | 全部返回 DUPLICATE |
| 退款 | 20 线程 | 3 分钟 | P99 < 500ms |
| 混合场景 | 支付+查询+退款 | 100 线程 | 核心 P99 < 1s |

**压测报告模板：** 截图 + 关键指标整理到 `docs/benchmark-report.md`

### 4.4 Phase 4 验证

| 验证项 | 操作 | 预期 |
|--------|------|------|
| IP 频控 | 同一 IP 连续 100 次请求 | 第 51 次起返回 429 |
| 日累计限额 | 单商户累计支付超限 | 返回 LIMIT_EXCEEDED |
| Prometheus | 访问 /actuator/prometheus | 有自定义指标 |
| Grafana | 5 个面板均有数据 | 曲线正常 |
| 压测报告 | 执行全部场景 | 数据达标 |

---

## Phase 5：扩展功能（第 11-12 周，简历写好后再做）

- 转账接口（merchant → merchant）
- 提现接口（merchant → 外部卡，走模拟器）
- 钉钉 Webhook 告警接入
- 数据归档（XXL-JOB 按月迁移旧流水到归档表）
- Swagger 商户 SDK 文档完善

---

## 编码规范

- 所有 public 类/接口/方法必须有 Javadoc 注释（中文），说明用途、参数、返回值
- 关键业务逻辑块前加行注释解释「为什么这样做」（而非「做了什么」）
- 复杂条件判断拆分为有意义的布尔变量并注释
- 异常处理分支必须注释说明什么场景会触发
- 所有 Java 文件头部不加 `@author`，不加文件创建时间（Git 记录即可）
- DTO 字段加 `@Schema(description = "中文说明")` 供 Knife4j 生成文档
- 禁止 `// TODO` 形式的注释（有遗漏就现在做，不要留到以后）

## 跨模块关注点

### 分布式 ID 生成

所有模块使用 `SnowflakeIdGenerator`（定义在 payment-common）：

```java
/**
 * 分布式 Snowflake ID 生成器。
 * <p>基于 Hutool 的 Snowflake 算法实现，本地生成无网络开销。</p>
 * <p>workerId 通过 Nacos 配置下发给每个服务实例，确保全局唯一。</p>
 */
@Component
public class SnowflakeIdGenerator {

    private final Snowflake snowflake;

    /**
     * @param workerId 工作机器 ID（1-31），通过 Nacos 配置下发
     */
    public SnowflakeIdGenerator(@Value("${snowflake.worker-id}") long workerId) {
        this.snowflake = IdUtil.getSnowflake(workerId, 1);
    }

    /** @return 全局唯一 long 型 ID */
    public long nextId() {
        return snowflake.nextId();
    }

    /** @return 全局唯一字符串型 ID */
    public String nextIdStr() {
        return snowflake.nextIdStr();
    }
}
```

workerId 通过 Nacos 配置下发给每个实例：
- payment-gateway: workerId = 1
- account-service: workerId = 2
- order-service: workerId = 3
- notification-service: workerId = 4
- reconciliation-service: workerId = 5
- merchant-service: workerId = 6
- channel-simulator: workerId = 7

### 统一日志规范

```
[TRACE_ID] [MODULE] [LEVEL] message
例: [a1b2c3d4] [GATEWAY] [INFO] 支付下单成功 outTradeNo=xxx amount=100
```

### 数据库初始化

**文件:** `sql/init-ds0.sql`（Docker Compose 启动时自动执行）

```sql
CREATE TABLE account_0 (
    id BIGINT PRIMARY KEY,
    merchant_id BIGINT NOT NULL COMMENT '商户ID（分片键）',
    balance DECIMAL(18,2) DEFAULT 0 COMMENT '账户总余额',
    frozen_amount DECIMAL(18,2) DEFAULT 0 COMMENT '冻结金额',
    version INT DEFAULT 0 COMMENT '乐观锁版本号',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_merchant_id (merchant_id)
) ENGINE=InnoDB COMMENT='账户表';

-- account_1 ~ account_7 结构相同

CREATE TABLE transaction_0 (
    id BIGINT PRIMARY KEY,
    txn_id VARCHAR(32) NOT NULL COMMENT '交易流水号（Snowflake）',
    merchant_id BIGINT NOT NULL COMMENT '商户ID（分片键）',
    amount DECIMAL(18,2) NOT NULL COMMENT '交易金额',
    txn_type VARCHAR(32) NOT NULL COMMENT '交易类型：PAY/REFUND/FREEZE/UNFREEZE',
    out_trade_no VARCHAR(64) COMMENT '外部订单号（幂等键）',
    status VARCHAR(16) COMMENT '交易状态',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_merchant_id (merchant_id),
    INDEX idx_txn_id (txn_id),
    INDEX idx_out_trade_no (out_trade_no)
) ENGINE=InnoDB COMMENT='交易记录表';

CREATE TABLE journal_entry_0 (
    id BIGINT PRIMARY KEY,
    txn_id VARCHAR(32) NOT NULL COMMENT '交易流水号',
    debit_account_id BIGINT NOT NULL COMMENT '借方账户ID',
    credit_account_id BIGINT NOT NULL COMMENT '贷方账户ID',
    amount DECIMAL(18,2) NOT NULL COMMENT '交易金额',
    dr_cr_flag CHAR(1) NOT NULL COMMENT '借贷标识：D=借方 C=贷方',
    txn_type VARCHAR(32) NOT NULL COMMENT '交易类型',
    txn_time DATETIME NOT NULL COMMENT '交易时间',
    merchant_id BIGINT NOT NULL COMMENT '商户ID（分片键）',
    INDEX idx_merchant_id (merchant_id),
    INDEX idx_txn_id (txn_id)
) ENGINE=InnoDB COMMENT='复式记账流水表';
```

---

## 验证清单（最终交付标准）

| # | 验证项 | 标准 |
|---|--------|------|
| 1 | 依赖冲突 | `mvn dependency:tree` 无 conflict |
| 2 | 编译 | `mvn clean compile` 全模块通过 |
| 3 | Docker Compose | `docker-compose up -d` 13 容器全部 healthy |
| 4 | 服务注册 | 7 服务在 Nacos 全部可见 |
| 5 | 支付全链路 | 商户请求 → 渠道 → TCC → MQ → 订单 → 回调，端到端通 |
| 6 | 幂等性 | 重复请求返回 DUPLICATE + 原结果 |
| 7 | 并发扣款 | 50 线程并发扣同一商户，无超卖 |
| 8 | 分库分表 | 数据按 merchant_id 路由到正确分片 |
| 9 | 渠道 UNKNOWN | 超时返回 PROCESSING + 后台查单正确 |
| 10 | TCC 异常回滚 | Cancel 后余额恢复初始值 |
| 11 | Canal 对账 | Binlog → Kafka → 消费 → 比对正确 |
| 12 | 回调重试 | 商户不可达时递增退避重试 |
| 13 | Sentinel 限流 | 超限返回 429 |
| 14 | Grafana | 5 个 Dashboard 有实时数据 |
| 15 | 压测 | 500 QPS 正常 + 2000 QPS 峰值达标 |
| 16 | Swagger | Knife4j /doc.html 可访问 |
