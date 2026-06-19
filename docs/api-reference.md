# 分布式支付收银台 & 账务系统 — API 接口文档

> Base URL: `http://localhost:{port}`

---

## 一、商户服务 — merchant-service（8085）

### 1.1 商户入驻

```
POST /api/v1/merchant/register
Content-Type: application/json

Request:
{
    "merchantName": "小明科技",
    "contactEmail": "admin@xiaoming.com"
}

Response:
{
    "code": 0, "message": "success",
    "data": {
        "id": 1234567890,
        "merchantNo": "MCH1718800000abcd",
        "name": "小明科技",
        "status": "ACTIVE",
        "apiKey": "a1b2c3d4e5f6..."
    }
}
```

### 1.2 查询商户

```
GET /api/v1/merchant/{merchantId}

Response: { "code": 0, "data": { "id": ..., "name": ..., "status": "ACTIVE" } }
```

### 1.3 停用商户

```
PUT /api/v1/merchant/{merchantId}/disable

Response: { "code": 0, "data": null }
```

### 1.4 生成 RSA 密钥对

```
POST /api/v1/merchant/{merchantId}/key/generate

Response:
{
    "code": 0, "data": {
        "merchantId": 1234567890,
        "publicKey": "MIIBIjANBgkqhki...",
        "privateKey": "MIIEvQIBADANBgkqhki...",
        "keyType": "RSA"
    }
}
```

> ⚠️ 私钥仅返回一次，商户必须妥善保管。

### 1.5 获取商户公钥（内部）

```
GET /api/v1/merchant/{merchantId}/key/public

Response: { "code": 0, "data": "MIIBIjANBgkqhki..." }
```

### 1.6 配置费率

```
POST /api/v1/merchant/{merchantId}/rate
Content-Type: application/json

Request: { "channelType": "WECHAT", "feeRate": 0.0038 }
Response: { "code": 0, "data": { "channelType": "WECHAT", "feeRate": 0.0038 } }
```

### 1.7 查询费率（内部）

```
GET /api/v1/merchant/{merchantId}/rate/{channel}

Response: { "code": 0, "data": 0.0038 }
```

---

## 二、渠道模拟器 — channel-simulator（8086）

### 2.1 模拟发起支付

```
POST /api/v1/simulator/pay
Content-Type: application/json

Request: { "outTradeNo": "MCH001", "amount": 99.99, "channelType": "WECHAT" }

Response:
{
    "code": 0, "data": {
        "channelOrderNo": "CH1718800000abc",
        "outTradeNo": "MCH001",
        "amount": 99.99,
        "status": "SUCCESS",
        "message": "支付成功"
    }
}
```

> status 三态：SUCCESS（80%）/ UNKNOWN（10%）/ FAIL（10%）

### 2.2 模拟查单

```
GET /api/v1/simulator/query?outTradeNo=MCH001

Response: { "code": 0, "data": { "channelOrderNo": "CH...", "status": "SUCCESS" } }
```

### 2.3 模拟账单下载

```
GET /api/v1/simulator/bill/{date}?   (date: yyyy-MM-dd)

Response: { "code": 0, "data": [{ "channelOrderNo": "...", "amount": 99.99, "status": "SUCCESS" }] }
```

---

## 三、支付网关 — payment-gateway（8080）

### 3.1 支付下单（商户核心接口）

```
POST /api/v1/pay/create
Content-Type: application/json
X-Signature: <RSA签名Base64>
X-Timestamp: <秒级Unix时间戳>
X-Nonce: <32位随机字符串>
X-Forwarded-For: <客户端IP>  (可选)

Request:
{
    "outTradeNo": "MCH20240619001",
    "merchantId": 1234567890,
    "amount": 99.99,
    "currency": "CNY",
    "notifyUrl": "https://merchant.com/callback",
    "subject": "会员充值"
}

Response (成功):
{ "code": 0, "data": { "outTradeNo": "MCH001", "payStatus": "SUCCESS", "amount": 99.99, "channelOrderNo": "CH...", "paidTime": "2024-06-19T12:00:00" } }

Response (渠道处理中):
{ "code": 0, "data": { "outTradeNo": "MCH001", "payStatus": "PROCESSING" } }

Response (幂等重复):
{ "code": 20001, "message": "请求已处理，返回原结果" }

Response (签名失败):
{ "code": 40101, "message": "签名验证失败" }    HTTP Status: 401

Response (余额不足):
{ "code": 42201, "message": "账户余额不足" }     HTTP Status: 422

Response (风控拦截):
{ "code": 42202, "message": "超过单笔交易限额" } HTTP Status: 422

Response (频控):
{ "code": 50001, "message": "请求过于频繁..." }  HTTP Status: 429

Response (系统错误):
{ "code": 50001, "message": "系统繁忙..." }      HTTP Status: 500
```

**签名串构造规则：**
```
POST\n/api/v1/pay/create\n{timestamp}\n{nonce}\n{body_json}\n
```

### 3.2 支付查询

```
GET /api/v1/pay/query?outTradeNo=MCH001   (实际用 RequestBody)

Response: { "code": 0, "data": { "outTradeNo": "MCH001", "payStatus": "SUCCESS", "amount": 99.99 } }
```

---

## 四、账户服务 — account-service（8081）

### 4.1 余额查询

```
GET /api/v1/account/balance/{merchantId}

Response: { "code": 0, "data": { "accountId": 10001, "balance": 10000.00, "frozenAmount": 500.00, "availableBalance": 9500.00 } }
```

### 4.2 充值

```
POST /api/v1/account/recharge/{merchantId}?amount=10000.00&outTradeNo=RCH001

Response: { "code": 0, "data": null }
```

### 4.3 TCC Try（冻结）

```
POST /api/v1/account/tcc/try
Content-Type: application/json

Request: { "merchantId": 10001, "amount": 500.00, "bizOrderNo": "BIZ001" }

Response: { "code": 0, "data": { "tccId": "TCC1718800000abc", "frozenAmount": 500.00 } }
```

### 4.4 TCC Confirm（确认扣款）

```
POST /api/v1/account/tcc/confirm
Content-Type: application/json
Request: { "tccId": "TCC1718800000abc" }
Response: { "code": 0, "data": null }
```

### 4.5 TCC Cancel（释放冻结）

```
POST /api/v1/account/tcc/cancel
Content-Type: application/json
Request: { "tccId": "TCC1718800000abc" }
Response: { "code": 0, "data": null }
```

---

## 五、订单服务 — order-service（8082）

### 5.1 查询订单

```
GET /api/v1/order/{orderNo}
Response: { "code": 0, "data": { "orderNo": "ORD...", "outTradeNo": "...", "amount": 99.99, "status": "PAID" } }
```

### 5.2 申请退款

```
POST /api/v1/refund/apply?outRefundNo=REF001&originOrderNo=ORD001&merchantId=10001&refundAmount=50.00
Response: { "code": 0, "data": { "refundNo": "RFN...", "outRefundNo": "REF001", "status": "REFUNDING" } }
```

---

## 六、通知服务 — notification-service（8083）

### 6.1 查询通知记录

```
GET /api/v1/notification/record/{outTradeNo}
Response: { "code": 0, "data": { "outTradeNo": "...", "status": "SUCCESS", "retryCount": 1 } }
```

---

## 七、对账服务 — reconciliation-service（8084）

### 7.1 对账相关

> 对账服务无直接对外 REST API。对账由 Kafka Consumer + XXL-JOB 定时任务执行。

---

## 八、错误码速查

| 错误码 | HTTP | 说明 |
|--------|------|------|
| 0 | 200 | 成功 |
| 20001 | 200 | 幂等重复请求 |
| 20201 | 202 | 渠道处理中 |
| 40001 | 400 | 参数校验失败 |
| 40101 | 401 | 签名验证失败 |
| 40102 | 401 | 时间戳过期 |
| 40103 | 401 | nonce 重复 |
| 40301 | 403 | 商户不可用 |
| 42201 | 422 | 余额不足 |
| 42202 | 422 | 单笔限额 |
| 42203 | 422 | 日累计限额 |
| 42204 | 422 | 商户被风控 |
| 42205 | 422 | 渠道失败 |
| 50001 | 500 | 系统错误 |
