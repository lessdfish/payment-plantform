# JMeter 压测指南

## 前置条件

1. Docker 中间件全部启动：`docker-compose up -d`
2. 7 个服务全部启动：
   - merchant (8085), simulator (8086), gateway (8080)
   - account (8081), order (8082)
   - notification (8083), reconciliation (8084)

## 压测步骤

### 1. 生成测试密钥

在 IDEA 中运行 `SetupJMeterTest.main()`，会输出：
```
MERCHANT_ID=1234567890
PRIVATE_KEY=MIIEvQIBADANBgkqhki...
```

### 2. 导入 JMeter

1. 打开 Apache JMeter
2. File → Open → 选择 `jmeter/payment-stress-test.jmx`
3. 修改 User Defined Variables 中的 `MERCHANT_ID`、`PRIVATE_KEY` 为第 1 步输出的值

### 3. 添加签名生成器（JSR223 PreProcessor）

为每个支付请求添加 JSR223 PreProcessor：

```groovy
import cn.hutool.core.codec.Base64
import cn.hutool.crypto.asymmetric.Sign
import cn.hutool.crypto.asymmetric.SignAlgorithm

// 从 JMeter 变量读取
def merchantId = vars.get("MERCHANT_ID")
def privateKey = vars.get("PRIVATE_KEY")

// 生成随机参数
def timestamp = String.valueOf(System.currentTimeMillis() / 1000 as long)
def nonce = UUID.randomUUID().toString().replace("-", "")

// 构建签名串
def body = sampler.getArguments().getArgument(0).getValue()
def signContent = "POST\n/api/v1/pay/create\n${timestamp}\n${nonce}\n${body}\n"

// 签名
def sign = new Sign(SignAlgorithm.SHA256withRSA, privateKey, null)
def signature = Base64.encode(sign.sign(signContent.getBytes()))

// 设置 Header
sampler.addNonEncodedArgument("", signature, "")
```

> 注意：JMeter 需要将 Hutool JAR 放入 `JMETER_HOME/lib/ext/`：
> - `hutool-all-5.8.28.jar`
> - `F:/apache-maven-3.9.5/mvn_resp/cn/hutool/hutool-all/5.8.28/hutool-all-5.8.28.jar`

### 4. 运行压测

按场景逐个运行（勾选/取消勾选 ThreadGroup 的 enabled）：

| 场景 | 线程 | 时长 | 预期 |
|------|------|------|------|
| 正常支付 | 50 | 5min | P99 < 350ms |
| 峰值支付 | 200 | 2min | P99 < 1s |
| 幂等 | 100 | 1min | 全部 DUPLICATE |
| 退款 | 20 | 3min | P99 < 500ms |
| 混合 | 100 | 3min | 支付+查询 |

### 5. 查看结果

JMeter → Aggregate Report → 查看 Average / P99 / Error%

预期：
- QPS > 500（正常）/ QPS > 1000（峰值）
- 错误率 < 0.5%
- P99 < 1s
