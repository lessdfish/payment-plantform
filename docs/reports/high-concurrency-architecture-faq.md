# 高并发架构改造 & 扩展问题深度解答

> **日期：** 2026-06-25
> **目的：** 异步化改造 QPS 推算、10 万 QPS 方案、Go 改写对比、灰度发布、SDK、支付渠道接入

---

## 一、异步受理模式下单机 40,000 QPS 是如何推算的

### 1.1 推算公式

QPS 的本质是 **并发度 ÷ 单请求平均耗时**（Little's Law）。

```
QPS = 并发处理单元数 / 平均处理时间(s)
```

当前同步链路：

```
一个支付请求在 Gateway 内部做了什么：

  1. Redis 幂等查询           ~1ms    (网络 RTT)
  2. RSA 验签                  ~3ms    (CPU 密集型)
  3. Redis 风控检查            ~1ms    (网络 RTT)
  4. REST → merchant 查公钥    ~3ms    (网络 RTT + 序列化)
  5. REST → simulator 支付     ~53ms   (网络 RTT + 50ms 模拟延迟)
  6. REST → account TCC Try    ~5ms    (网络 RTT + ShardingSphere + DB)
  7. REST → account TCC Confirm ~5ms   (网络 RTT + ShardingSphere + DB)
  8. RocketMQ 发送             ~1ms    (网络 RTT)
  9. Redis 缓存幂等结果         ~1ms    (网络 RTT)
  ─────────────────────────────────────────
  合计                        ~73ms

Tomcat 默认 200 线程:
  200 / 0.073 = 2739 QPS（理论极限）

实测：
  - 网关 CPU 未打满时已受限于下游 simulator 的 50ms 延迟
  - 200 线程实际约 84-193 QPS（含完整 TCC + MQ + Canal 整条链路）
```

异步受理改造后：

```
Gateway 只做同步受理（验签 + 幂等 + 风控 + 入队），其余全异步：

  1. Redis 幂等查询           ~0.5ms   (本地 Redis，loopback)
  2. RSA 验签                  ~1ms     (公钥从 Caffeine 本地缓存取，不调 merchant)
  3. Redis 风控检查            ~0.5ms   (三个 Redis 命令: blacklist + ip rate + daily incr)
  4. RocketMQ 发送             ~0.5ms   (oneway 单向发送，不等 broker 确认)
  ─────────────────────────────────────────
  合计                        ~2.5ms   (全部是内存/loopback 操作)

JDK 21 虚拟线程（不再受 200 平台线程限制）:
  虚拟线程是 JVM 内部调度的轻量对象，一个虚拟线程 ≈ 几百字节栈内存
  单机可支撑 10 万+ 并发虚拟线程

  100,000 虚拟线程 / 0.0025s = 40,000,000？这显然不对
```

**问题在这里** — Little's Law 的"并发处理单元数"不是虚拟线程总数，而是**同一时刻真正在执行（不在等待 I/O）的虚拟线程数**。虚拟线程在等 I/O 时会被自动挂起（yield），把 CPU 让给其他虚拟线程。

```
正确的推算是用 CPU 时间而非线程数：

  CPU 核心数 = 8（假设 8C16G 机器）
  单笔请求纯 CPU 时间 ≈ 0.5ms（验签 0.3ms + JSON 序列化 0.1ms + 其他 0.1ms）
  其余 2ms 是等 I/O（Redis 网络 RTT、MQ 网络 RTT）
  等 I/O 时虚拟线程挂起，其他虚拟线程上 CPU

  8 核 × (1s / 0.0005s) = 16,000 QPS（CPU 瓶颈）
```

**所以"4 万 QPS"的准确来源：**

| 环节 | 优化后耗时 | 性质 |
|------|----------|------|
| 验签（Caffeine 缓存公钥） | 0.3ms | 纯 CPU |
| 幂等 Redis GET | 0.3ms | I/O 等待 |
| 风控 Redis pipeline（3 命令合并） | 0.3ms | I/O 等待 |
| MQ 单向发送 | 0.2ms | I/O 等待 |
| JSON 序列化 | 0.1ms | 纯 CPU |
| 其他（日志/MDC 等） | 0.1ms | 混合 |
| **合计** | **~1.3ms** | |
| 其中纯 CPU | ~0.5ms | |
| 其中 I/O 等待 | ~0.8ms | |

```
虚拟线程模型下：
  - 8 核，每核约 1/0.0005 = 2000 笔/秒 CPU 吞吐
  - 8 × 2000 = 16,000 QPS（纯 CPU 天花板）

但 Redis 和 MQ 的 I/O 等待期间，虚拟线程让出 CPU
实际有效 CPU 利用率可以从 25% 提升到接近 80-90%

压缩后的 CPU 时间 ≈ 总时间 × CPU 占比 = 1.3 × (0.5/1.3) = 0.5ms
I/O 期间有 8/0.3 = 约 26,000 个并发虚拟线程同时等 I/O

8 核 → 每核 2000 CPU-ops/s → 16,000 QPS（单机）
优化到极致（验签预计算、批量 Redis pipeline、zero-copy 序列化）:
  CPU 时间压到 0.2ms → 8 × (1/0.0002) = 40,000 QPS（单机理想上限）
```

**"4 万"取的是一个合理优化后的预期值，不是随便拍的数字。**

### 1.2 逐项论证

| 假设 | 数值 | 依据 |
|------|------|------|
| 单机 CPU 核数 | 8 | 常见的 8C16G 云服务器 |
| 单请求 CPU 耗时 | 0.2-0.5ms | 验签 + 序列化，公钥缓存，无 DB 调用 |
| I/O 等待期间 CPU 释放 | 是 | JDK 21 虚拟线程核心特性 |
| Redis 单机 QPS | 10 万+ | loopback 网卡，pipeline 模式 |
| RocketMQ oneway 发送 | 10 万+ | 不等 broker 返回，纯异步 |

**结论：异步受理模式下单机 20,000-40,000 QPS 是一个有理有据的范围。4 万是理想上限，2 万是保守估计。**

---

## 二、压测如何设置才能验证这个数字

### 2.1 核心问题：当前压测打不上去的原因

当前项目的瓶颈不在网关，而在**下游同步链路**：

```
每次支付请求必须等：
  simulator（50ms 固定延迟）
  + account TCC Try（DB 操作 + 分片路由）
  + account TCC Confirm（DB 操作 + 复式流水）
  + Kafka txn-log 发送

这些累加起来 ≈ 50-100ms，远大于网关自身的 2-3ms
```

### 2.2 验证异步受理 QPS 的压测方案

**第一步：隔离开关**

```
在 payment-gateway 中新增一个独立接口：

POST /api/v1/pay/async-only
  → 验签 + 幂等 + 风控 + MQ 发送 → 立即返回 ACCEPTED
  → 不调 simulator、不调 account
```

这个接口用来**纯测网关的受理能力**，排除下游干扰。

**第二步：JMeter 配置**

```
场景：验证异步受理单机上限
  线程：500 → 1000 → 2000（阶梯递增）
  时长：每阶梯 2 min
  outTradeNo：多商户（1000 商户 × N 笔），避免单分片热点
  请求体：{"outTradeNo":"ASYNC_${merchantId}_${__time()}_${__threadNum}","merchantId":${__Random(1,1000)},...}
```

**第三步：观察指标**

```
监听项                      判断标准
─────────────────────────────────────────
JMeter Aggregate Report     QPS 随线程增长，直到不再增长（触及 CPU 天花板）
Payment-gateway CPU         达到 80-90%（8 核中 6-7 核满载）
Redis CPU                   稳定 < 50%（未成为瓶颈）
RocketMQ broker 积压        开始出现少量积压（消费者在消化）
错误率                      保持在 < 0.1%
```

**第四步：分段验证**

| 阶段 | 关掉什么 | 测什么 | 目标 |
|------|---------|--------|------|
| A | 关 simulator + account | Gateway 纯验签速度 | 5 万+ QPS |
| B | 关 account，开 simulator（delayMs=0） | Gateway + 渠道 | 1-2 万 QPS |
| C | 全部开启（异步受理模式） | 完整链路受理 | 2 万+ QPS |
| D | 全部开启 + 消费者正常 | 端到端吞吐 | 消费者 TPS ≤ 数据库承载 |

### 2.3 为什么不能一步到位

```
原因 1: 数据库是最终瓶颈
  ShardingSphere 4 库 40 个 HikariCP 连接，单库约 500-1000 TPS 写入
  4 库 ≈ 2000-4000 TPS（最乐观）
  消费者处理速度不可能超过这个数字

原因 2: MQ 消费者是串行的
  RocketMQ MessageListener 默认单线程消费
  需要设置 setConsumeThreadMax(20-50) 才能提升

原因 3: 这才是真实的生产级挑战
  受理快 ≠ 结算快
  架构的价值在于"受理和结算可以独立扩容"
```

---

## 三、改进到 10 万 QPS 的技术方案和可行性分析

### 3.1 总体架构

```
                        ┌─────────────┐
                        │   LVS/DPVS   │  四层负载，ECMP 多活
                        └──────┬──────┘
                               │
                    ┌──────────┴──────────┐
                    │                     │
              ┌─────┴─────┐         ┌─────┴─────┐
              │ Nginx x N │  ...    │ Nginx x N │  七层卸载 SSL + 限流
              └─────┬─────┘         └─────┬─────┘
                    │                     │
         ┌──────────┴──────────┐  ┌───────┴───────┐
         │  Gateway 集群 x 10  │  │  Gateway 集群   │  受理层（无状态）
         │  (只做验签+入队)     │  │  (可弹性伸缩)   │
         └──────────┬──────────┘  └───────┬───────┘
                    │                     │
                    └──────────┬──────────┘
                               │
                    ┌──────────┴──────────┐
                    │  RocketMQ 集群       │  削峰缓冲
                    │  (16-32 分区)        │
                    └──────────┬──────────┘
                               │
         ┌─────────────────────┼─────────────────────┐
         │                     │                     │
  ┌──────┴──────┐      ┌──────┴──────┐      ┌──────┴──────┐
  │ 结算消费者   │      │ 通知消费者   │      │ 对账消费者   │
  │ x 50 实例   │      │ x 20 实例   │      │ x 10 实例   │
  └──────┬──────┘      └─────────────┘      └─────────────┘
         │
  ┌──────┴──────────────────┐
  │  account-service 集群    │
  │  (8-16 实例)            │
  └──────┬──────────────────┘
         │
  ┌──────┴──────────────────┐
  │  MySQL 分片集群          │
  │  16 库 × 8 表 = 128 分片 │
  └─────────────────────────┘
```

### 3.2 逐层改造清单

| 层级 | 当前状态 | 目标状态 | 改造量 |
|------|---------|---------|--------|
| 负载均衡 | 无（直连 localhost） | LVS/DPVS → Nginx 集群 | 新增 |
| 网关 | 同步 70ms | 异步受理 2-3ms + 验签本地缓存 | 改 PayServiceImpl |
| 缓存 | Redis 单机 | Redis Cluster 6 节点 | 新增部署 |
| 消息 | RocketMQ 单 broker | 集群 + 32 分区 | 新增部署 |
| 消费者 | 单线程默认 | 并发消费 + 批量提交 | 改配置 |
| 数据库 | 4 库 × 8 表 | 16 库 × 8 表 + 读写分离 | 新增部署 + 改 ShardingConfig |
| 序列化 | JSON (Jackson) | Protobuf | 改所有 DTO |
| 部署 | 本机 Java -jar | K8s + HPA 自动伸缩 | 新增 |

### 3.3 可行性分析

| 风险点 | 严重程度 | 缓解方案 |
|--------|---------|---------|
| 异步化后商户如何获知结果 | 高 | 提供查询接口 + 主动回调通知（已有） |
| MQ 消息丢失导致资金不一致 | 高 | RocketMQ 事务消息 + 定时对账兜底（已实现） |
| 数据库写入成为瓶颈 | 高 | 分库数翻倍 + 批量写入 + 异步刷盘 |
| 热点商户导致分片不均 | 中 | 一致性哈希 + 热点自动迁移 |
| Redis 缓存穿透 | 中 | 布隆过滤器 + 空值缓存 |
| 虚拟线程在 synchronized 上 pin 住 | 中 | 替换为 ReentrantLock |
| Protobuf 改造量 | 中 | 渐进式：先内部服务间，网关对外保持 JSON |

### 3.4 人力与时间估算

```
Phase A: 异步受理改造        2 人 × 3 周
Phase B: 缓存 + MQ 集群化    2 人 × 4 周
Phase C: 数据库扩容          1 人 × 2 周
Phase D: 容器化 + K8s        1 人 × 3 周
Phase E: 压测 + 调优         2 人 × 2 周
────────────────────────────────────
合计: 约 3 个月（3-4 人团队）
```

**结论：可行，但前提是业务真有 10 万 QPS 的需求。本项目的简历定位下，异步受理方案做到 5000-20000 QPS 单机就已经很有说服力。**

---

## 四、如果用 Go 改写，优化的体现

### 4.1 性能层面

| 维度 | Java（当前） | Go | 差距 |
|------|------------|-----|------|
| 内存占用 | 每服务 200-500MB（JVM堆+元空间+直接内存） | 每服务 20-50MB | **10x** |
| 启动时间 | 3-8 秒（Spring 容器启动） | 0.1-1 秒 | **10-50x** |
| GC 停顿 | 1-50ms（G1/ZGC） | < 1ms（并发三色标记） | **10x** |
| 并发模型 | 虚拟线程（JDK 21） | goroutine（原生） | 接近，Go 更轻量 |
| 序列化 | 反射 + Jackson | 编译期代码生成 | Go 快 2-3x |
| 网络 I/O | Netty（如果用 WebFlux）/ Servlet | 原生 netpoller | 接近 |
| 单核 CPU 效率 | JIT 预热后接近 C++ | 原生编译，无预热 | 冷启动 Go 更快 |

**结论：Go 内存省 10 倍，启动快 10-50 倍，GC 几乎无感。但稳定状态下的吞吐差距不大（Java JIT 预热后接近 Go）。**

### 4.2 工程层面

| 维度 | Java | Go |
|------|------|-----|
| 生态完整性 | Spring 全家桶、ShardingSphere 开箱即用 | 需自建 ORM/分库分表/RPC 框架 |
| 依赖管理 | Maven/Gradle，JAR 冲突常见 | go mod，编译时依赖锁定 |
| 部署 | 需要 JRE 21（200MB+） | 单二进制文件（10-20MB） |
| 开发效率 | 注解/反射/AOP 生产力高 | 显式代码，少魔法 |
| 招聘市场 | Java 岗位数量是 Go 的 3-5 倍 | Go 集中在云原生/中间件 |
| 可观测性 | 字节码增强（SkyWalking agent） | 需代码侵入或 eBPF |

### 4.3 哪些场景 Go 明显优于 Java

1. **容器化部署**：二进制体积小、冷启动快，K8s 弹性伸缩友好
2. **网关/代理层**：低内存、高并发连接，Go netpoller 天然合适
3. **CLI 工具/运维脚本**：单文件分发，不依赖 JRE
4. **中间件**：etcd、TiDB、CockroachDB、Docker 都是 Go 写的

### 4.4 本项目是否适合 Go 改写

```
适合用 Go 重写的部分：
  ✅ payment-gateway（受理层，IO 密集 + 低延迟）
  ✅ channel-simulator（高性能 Mock）
  ✅ notification-service（大量 HTTP 回调 + 重试）

不适合用 Go 重写的部分：
  ❌ account-service（重度依赖 ShardingSphere 分库分表，Go 无等价成熟方案）
  ❌ reconciliation-service（依赖 XXL-JOB、Canal，Java 生态独占）

结论：混合架构可行——受理层 Go + 结算层 Java，但简历项目的核心竞争力
在 Java 的分布式事务深度，不建议改写。
```

### 4.5 简历话术（如果面试官问"为什么用 Java 不用 Go"）

> "支付核心（account-service）重度依赖 ShardingSphere 分库分表、TCC 事务框架，
> Java 生态在这一层有最成熟的解决方案。如果业务量增长到需要独立受理层，
> 网关部分可以考虑用 Go 重写以降低延迟和内存开销，但账务核心会保留 Java。"

---

## 五、200-400 台机器是怎么管理的

### 5.1 首先：后端开发需要关心选机器吗？

**大部分不需要，但有两次你一定会碰到：**

| 场景 | 谁会碰到 |
|------|---------|
| 日常开发 | 不需要。运维/DevOps/SRE 负责 |
| 压测调优 | **你需要** — 判断瓶颈在哪里（CPU/内存/IO/网络），申请对应规格的机器 |
| 容量规划 | **高级工程师需要** — 根据 QPS 目标和压测数据，算出需要多少台机器的资源预算 |
| 技术选型 | **架构师/TL 需要** — 决定计算型用高主频还是通用型、存储型用本地 SSD 还是云盘 |

### 5.2 200 台机器是怎样增减的

**不会手动一台台操作。** 全部通过容器编排系统自动管理：

```yaml
# K8s HorizontalPodAutoscaler 配置示例
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
metadata:
  name: payment-gateway-hpa
spec:
  scaleTargetRef:
    kind: Deployment
    name: payment-gateway
  minReplicas: 10        # 最少 10 个实例
  maxReplicas: 200       # 最多 200 个实例
  metrics:
  - type: Resource
    resource:
      name: cpu
      target:
        type: Utilization
        averageUtilization: 70   # CPU 超过 70% 就扩容
  - type: Pods
    pods:
      metric:
        name: http_requests_per_second
      target:
        type: AverageValue
        averageValue: "5000"     # 单实例超过 5000 QPS 就扩容
```

```
流程：

  压测得出 → 单实例承载上限 = 5000 QPS
  目标 QPS = 100,000
  需要实例 = 100,000 / 5,000 × 1.5(冗余) = 30 个实例
  再按 CPU/内存反推 → 需要多少台物理机/虚拟机

  日常 30 个实例，大促时 HPA 自动拉到 60-80 个
  物理机层面由运维/K8s 集群自动调度
```

**后端开发需要掌握的是：你负责的服务单实例能扛多少 QPS，这个数字通过压测得出来。其他都是自动的。**

---

## 六、灰度发布是怎么推的

### 6.1 原理

```
不灰度：全量发布 → 100% 用户立刻看到新版本 → 出 BUG 全部完蛋

灰度：先把 1% → 5% → 20% → 50% → 100%，逐步放量
每步观察错误率/延迟/用户反馈 → 异常则立即回滚
```

### 6.2 实现方式

**方式一：Nginx/网关层按 UID 分流（最常用）**

```nginx
# OpenResty Lua 脚本
local uid = ngx.var.cookie_uid
local hash = ngx.crc32_long(uid)
local ratio = 5  -- 5% 灰度

if hash % 100 < ratio then
    ngx.var.upstream = "backend_canary"   -- 灰度版本
else
    ngx.var.upstream = "backend_stable"   -- 稳定版本
end
```

**方式二：K8s Ingress Canary**

```yaml
apiVersion: networking.k8s.io/v1
kind: Ingress
metadata:
  annotations:
    nginx.ingress.kubernetes.io/canary: "true"
    nginx.ingress.kubernetes.io/canary-weight: "5"   # 5% 流量
```

**方式三：Service Mesh（Istio/Envoy）**

```yaml
apiVersion: networking.istio.io/v1beta1
kind: VirtualService
spec:
  http:
  - route:
    - destination:
        host: payment-gateway
        subset: stable
      weight: 95
    - destination:
        host: payment-gateway
        subset: canary
      weight: 5
```

### 6.3 灰度观察期做什么

| 时间 | 动作 |
|------|------|
| 发布后 5 min | 看错误率、P99 延迟有无异常 |
| 发布后 30 min | 看业务指标（支付成功率有无下降） |
| 发布后 1 h | 确认无异常 → 扩到 20% |
| 每步稳定后 | 逐步扩到 50% → 100% |
| 任一步异常 | **立即将 canary weight 设为 0** → 回滚到全量 stable |

### 6.4 本项目的灰度方案

```
当前（单机开发）: 不需要灰度，直接重启就行

如果上线:
  1. Nacos 注册两个 payment-gateway 服务组: STABLE / CANARY
  2. 商户在请求时网关根据 merchantId hash 分流
  3. 先让测试商户走 CANARY → 观察一天 → 逐步向普通商户开放
```

---

## 七、SDK 是什么

### 7.1 一句话

**SDK = 把调用你的服务的复杂逻辑封装成一个库（JAR / npm 包 / pip 包），商户引入后一行代码即可接入。**

### 7.2 为什么需要 SDK

```
没有 SDK:
  商户开发者需要:
    1. 读懂你的 API 文档
    2. 自己处理 RSA 签名（容易写错）
    3. 自己处理 nonce / timestamp 防重放
    4. 自己处理回调验签
    5. 自己处理重试/超时
  → 接入成本高、容易出错、客服压力大

有 SDK:
  商户开发者只需要:
    <dependency>  // 引入 JAR
    paymentClient.pay(request)  // 一行代码
  SDK 内部自动处理签名、防重放、回调验签、重试
```

### 7.3 SDK 的实现（以本项目为例）

```java
// payment-api 模块（已存在于项目中）
// 这就是你的 SDK

// 商户使用你的 SDK 的代码：
public class MerchantApp {
    public static void main(String[] args) {
        // 初始化（一次）
        PaymentClient client = PaymentClient.builder()
            .baseUrl("https://api.your-payment.com")
            .merchantId("MCH20240001")
            .privateKey(FileUtil.readString("merchant_private.key"))
            .publicKey(FileUtil.readString("platform_public.key"))
            .build();

        // 发起支付（一行）
        PayResponse result = client.pay(PayRequest.builder()
            .outTradeNo("ORDER_" + System.currentTimeMillis())
            .amount(new BigDecimal("99.99"))
            .currency("CNY")
            .notifyUrl("https://merchant.com/callback")
            .subject("测试商品")
            .build());

        System.out.println("支付结果: " + result.getPayStatus());
    }
}
```

SDK 内部封装了：

```
1. RSA 签名 → SDK 自动生成签名放入 Header
2. nonce/timestamp → SDK 每次请求自动生成
3. HTTP 调用 → SDK 内部使用 OkHttp + 连接池
4. 回调验签 → SDK 提供 verifyCallback(body, signature) 方法
5. 重试策略 → 网络超时自动重试 2 次
6. 序列化 → 对象 ↔ JSON，商户不需要关心
```

**你的 payment-api 模块就是这个角色，只是目前还是空壳，Phase 5 可以补上。**

---

## 八、如何在平台中接入微信支付、银行卡支付、支付宝支付

### 8.1 原理 — 聚合支付的本质

```
                     ┌──────────────────┐
                     │   你的支付平台      │
                     │  (Payment Gateway) │
                     └────────┬─────────┘
                              │
              ┌───────────────┼───────────────┐
              │               │               │
     ┌────────┴────────┐ ┌───┴────────┐ ┌───┴──────────┐
     │ WechatPayChannel │ │AlipayChannel│ │BankCardChannel│
     │   (适配器)       │ │  (适配器)   │ │  (适配器)     │
     └────────┬────────┘ └───┬────────┘ └───┬──────────┘
              │               │               │
     ┌────────┴────────┐ ┌───┴────────┐ ┌───┴──────────┐
     │  微信支付 API    │ │ 支付宝 API  │ │  银联/网联 API │
     │ api.mch.weixin  │ │ open.alipay │ │  unionpay.com │
     └─────────────────┘ └────────────┘ └──────────────┘
```

### 8.2 统一接口（策略模式）

```java
/**
 * 支付渠道统一接口 — 所有渠道必须实现。
 * 每接入一个新渠道，只需新增一个实现类，不改任何上游代码。
 */
public interface IPaymentChannel {

    /** 发起支付 */
    ChannelPayResult pay(ChannelPayRequest request);

    /** 查询支付结果 */
    ChannelQueryResult query(String outTradeNo);

    /** 退款 */
    ChannelRefundResult refund(ChannelRefundRequest request);

    /** 下载对账单 */
    List<BillLine> downloadBill(String date);

    /** 验证渠道回调签名 */
    boolean verifyCallback(String body, String signature);

    /** 获取渠道类型 */
    ChannelEnum getChannelType();
}
```

### 8.3 各渠道的差异点

| 差异点 | 微信支付 | 支付宝 | 银联/网联 |
|--------|---------|--------|----------|
| API 协议 | HTTPS + XML/JSON | HTTPS + JSON | HTTPS + ISO 8583/JSON |
| 签名算法 | MD5 或 HMAC-SHA256（V3 用 RSA） | RSA2 (SHA256WithRSA) | SHA256WithRSA |
| 支付方式 | JSAPI / Native / H5 / APP | 当面付 / 手机网站 / APP | 网关支付 / 快捷支付 |
| 回调通知 | 微信服务器 POST JSON | 支付宝服务器 POST 表单 | 银行异步通知 |
| 结算周期 | T+1 | T+1 | T+1 |
| 接入流程 | 商户平台申请 → AppID + APIv3密钥 + 证书 | 开放平台申请 → AppID + 私钥 + 支付宝公钥 | 银行签约 → 商户号 + 证书 |

### 8.4 技术实现（以接入微信支付为例）

**第一步：渠道适配器**

```java
@Component
public class WechatPayChannel implements IPaymentChannel {

    // 微信支付配置（从 Nacos 动态加载）
    @Value("${wechat.pay.app-id}")
    private String appId;
    @Value("${wechat.pay.mch-id}")
    private String mchId;
    @Value("${wechat.pay.api-v3-key}")
    private String apiV3Key;

    // 微信支付 HTTP 客户端（使用微信官方 SDK 或自行封装）
    private final WechatPayHttpClient httpClient;

    @Override
    public ChannelPayResult pay(ChannelPayRequest request) {
        // 1. 构造微信统一下单请求
        WechatUnifiedOrderRequest wxReq = WechatUnifiedOrderRequest.builder()
            .appid(appId)
            .mchid(mchId)
            .outTradeNo(request.getOutTradeNo())
            .amount(WechatAmount.of(request.getAmount()))  // 元 → 分
            .description(request.getSubject())
            .notifyUrl("https://your-platform.com/api/v1/callback/wechat")  // 回调地址
            .build();

        // 2. 调用微信支付 API（HTTPS POST + 签名）
        WechatUnifiedOrderResponse wxResp = httpClient.unifiedOrder(wxReq);

        // 3. 转换为内部统一格式
        return ChannelPayResult.builder()
            .channelOrderNo(wxResp.getTransactionId())
            .outTradeNo(request.getOutTradeNo())
            .status(mapWechatStatus(wxResp.getTradeState()))
            .channelResponse(wxResp)                            // 保留原始响应
            .build();
    }

    @Override
    public boolean verifyCallback(String body, String signature) {
        // 微信 V3 回调验签：用微信平台公钥验签
        return WechatPayValidator.verify(body, signature, wechatPublicKey);
    }

    // ... 其他方法实现
}
```

**第二步：渠道路由改造（已有基础）**

```java
@Service
public class ChannelRouterServiceImpl implements ChannelRouterService {

    // Spring 自动注入所有 IPaymentChannel 实现
    private final Map<String, IPaymentChannel> channelMap;

    public ChannelRouterServiceImpl(List<IPaymentChannel> channels) {
        // 启动时自动发现所有渠道实现
        this.channelMap = channels.stream()
            .collect(Collectors.toMap(
                c -> c.getChannelType().name(),
                Function.identity()
            ));
    }

    @Override
    public RouteResult route(Long merchantId, BigDecimal amount) {
        // 现有逻辑：查商户费率，选最便宜的渠道
        List<RateConfig> rates = configService.getRates(merchantId);

        RateConfig selected = rates.stream()
            .filter(r -> channelMap.containsKey(r.getChannelType()))
            .min(Comparator.comparing(RateConfig::getFeeRate))
            .orElseThrow(() -> new BusinessException(ErrorCode.NO_AVAILABLE_CHANNEL));

        return RouteResult.builder()
            .channelType(selected.getChannelType())    // WECHAT / ALIPAY / UNIONPAY
            .channel(channelMap.get(selected.getChannelType().name()))
            .feeRate(selected.getFeeRate())
            .build();
    }
}
```

**第三步：回调统一入口**

```java
@RestController
@RequestMapping("/api/v1/callback")
public class CallbackController {

    private final Map<String, IPaymentChannel> channelMap;

    /**
     * 统一渠道回调入口（微信/支付宝/银联回调都到这里）
     */
    @PostMapping("/{channel}")
    public String handleCallback(
            @PathVariable String channel,
            @RequestBody String body,
            @RequestHeader Map<String, String> headers) {

        IPaymentChannel channelImpl = channelMap.get(channel);
        // 1. 验签
        String signature = headers.get("X-Signature"); // 或根据渠道取对应 header
        if (!channelImpl.verifyCallback(body, signature)) {
            return "FAIL";
        }

        // 2. 解析回调内容
        CallbackData data = channelImpl.parseCallback(body);

        // 3. 更新订单、触发结算、回调商户
        payService.handleChannelCallback(data);

        // 4. 返回成功应答（微信/支付宝要求返回特定字符串）
        return channelImpl.getCallbackSuccessResponse();
    }
}
```

### 8.5 一个真实接入需要做的事（checklist）

```
微信支付:
  □ 注册微信商户平台 → 获取商户号(mchId)
  □ 申请 APIv3 密钥 + 下载证书
  □ 配置支付域名 + 回调地址（必须是 HTTPS）
  □ 引入 wechatpay-apache-httpclient Maven 依赖
  □ 实现 WechatPayChannel 适配器
  □ 编写沙箱环境测试用例

支付宝:
  □ 注册支付宝开放平台 → 创建应用 → 获取 AppID
  □ 生成 RSA2 密钥对，上传公钥，获取支付宝公钥
  □ 引入 alipay-sdk-java Maven 依赖
  □ 实现 AlipayChannel 适配器
  □ 编写沙箱环境测试用例

银联:
  □ 签约银行 → 获取商户号 + 证书(.pfx)
  □ 引入银联 SDK 或自行实现 ISO 8583 报文
  □ 实现 UnionPayChannel 适配器

通用:
  □ 统一 IPaymentChannel 接口
  □ 渠道路由支持多渠道选择
  □ 统一回调入口 + 验签 + 应答
  □ 对账模块适配不同渠道的账单格式
```

### 8.6 对当前项目的影响

```
当前 channel-simulator 就是一个硬编码的 Mock 渠道。换成真实渠道时：

  改动范围：
    ✅ IPaymentChannel 接口新增（payment-common）
    ✅ WechatPayChannel / AlipayChannel 实现（新建模块或在 gateway 内）
    ✅ ChannelRouterService 改造（已有，只需加 @Autowired List<IPaymentChannel>）
    ✅ CallbackController 改造（已有预留接口）
    ✅ channel-simulator 保留作为压测和调试用 Mock
    ❌ 不改动 account-service、order-service、notification-service
```

这就是策略模式的威力 — 接一个新渠道，只加一个实现类，不碰核心业务代码。

---

## 九、总结

| 问题 | 答案 |
|------|------|
| 4 万 QPS 怎么算的 | Little's Law + CPU 瓶颈 = 8核 × 5000 ops/s = 40,000 QPS |
| 压测怎么测到 | 隔离下游 → 阶梯增加线程 → CPU 80% 时 QPS 不再增长即为上限 |
| 10 万 QPS 可行吗 | 可行，需 10+ 台网关 + 异步改造 + 数据库扩分片，约 3 个月 |
| Go 改写好吗 | 受理层适合，结算层 Java 生态更成熟，建议混合架构 |
| 200 台机器谁管 | DevOps/K8s HPA 自动管，你只需知道单实例扛多少 QPS |
| 灰度怎么推 | Nginx/K8s/Istio 按 uid hash 分比例，先 1% → 5% → 渐进全量 |
| SDK 是什么 | 把签名/验签/重试/序列化封成 JAR，商户一行代码接入 |
| 接入微信支付怎么做 | IPaymentChannel 接口 + 渠道适配器 + 策略模式，加一个类不碰核心 |
