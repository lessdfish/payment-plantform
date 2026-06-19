package com.payment.platform.gateway;

import cn.hutool.core.util.IdUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.payment.platform.common.dto.request.ChannelPayRequest;
import com.payment.platform.common.dto.request.PayRequest;
import com.payment.platform.common.dto.response.ChannelPayResponse;
import com.payment.platform.common.dto.response.PayResponse;
import com.payment.platform.common.result.ApiResult;
import com.payment.platform.common.util.RsaSignUtil;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.security.KeyPair;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 支付全链路集成测试。
 * <p><b>运行前需要确保 Docker 中间件和 3 个服务已启动：</b></p>
 * <ul>
 *   <li>Docker: docker-compose up -d</li>
 *   <li>merchant-service: 8085</li>
 *   <li>channel-simulator: 8086</li>
 *   <li>payment-gateway: 8080</li>
 * </ul>
 *
 * <p>运行方式：IDEA 右键 → Run 'PaymentIntegrationTest'</p>
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class PaymentIntegrationTest {

    private static final RestClient CLIENT = RestClient.create();
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final String MERCHANT_URL = "http://localhost:8085/api/v1/merchant";
    private static final String GATEWAY_URL = "http://localhost:8080/api/v1/pay";

    private static Long merchantId;
    private static String publicKey;
    private static String privateKey;
    private static String outTradeNo;

    /**
     * 步骤 1：注册商户。
     */
    @Test
    @Order(1)
    void step1_registerMerchant() throws Exception {
        String requestBody = """
                {
                    "merchantName": "测试商户",
                    "contactEmail": "test@payment.com"
                }
                """;

        String response = CLIENT.post()
                .uri(MERCHANT_URL + "/register")
                .contentType(MediaType.APPLICATION_JSON)
                .body(requestBody)
                .retrieve()
                .body(String.class);

        System.out.println("=== 商户注册响应 ===");
        System.out.println(response);

        @SuppressWarnings("unchecked")
        Map<String, Object> result = MAPPER.readValue(response, Map.class);
        Map<String, Object> data = (Map<String, Object>) result.get("data");

        assertNotNull(data, "商户注册失败");
        assertNotNull(data.get("id"), "商户 ID 为空");
        assertEquals("ACTIVE", data.get("status"));

        merchantId = Long.valueOf(data.get("id").toString());
        System.out.println("商户 ID: " + merchantId);
    }

    /**
     * 步骤 2：生成 RSA 密钥对。
     */
    @Test
    @Order(2)
    void step2_generateKeyPair() throws Exception {
        String response = CLIENT.post()
                .uri(MERCHANT_URL + "/" + merchantId + "/key/generate")
                .retrieve()
                .body(String.class);

        System.out.println("=== 密钥生成响应 ===");
        System.out.println(response);

        @SuppressWarnings("unchecked")
        Map<String, Object> result = MAPPER.readValue(response, Map.class);
        Map<String, Object> data = (Map<String, Object>) result.get("data");

        assertNotNull(data, "密钥生成失败");
        publicKey = (String) data.get("publicKey");
        privateKey = (String) data.get("privateKey");

        assertNotNull(publicKey, "公钥为空");
        assertNotNull(privateKey, "私钥为空");
        assertEquals("RSA", data.get("keyType"));

        System.out.println("公钥长度: " + publicKey.length());
        System.out.println("私钥长度: " + privateKey.length());
    }

    /**
     * 步骤 3：配置微信渠道费率。
     */
    @Test
    @Order(3)
    void step3_configureRate() throws Exception {
        String requestBody = """
                {
                    "channelType": "WECHAT",
                    "feeRate": 0.0038
                }
                """;

        String response = CLIENT.post()
                .uri(MERCHANT_URL + "/" + merchantId + "/rate")
                .contentType(MediaType.APPLICATION_JSON)
                .body(requestBody)
                .retrieve()
                .body(String.class);

        System.out.println("=== 费率配置响应 ===");
        System.out.println(response);

        @SuppressWarnings("unchecked")
        Map<String, Object> result = MAPPER.readValue(response, Map.class);
        assertNotNull(result.get("data"), "费率配置失败");

        System.out.println("费率: 0.38%");
    }

    /**
     * 步骤 4：构造签名并发起支付请求。
     * <p>这是核心验收点——验证完整支付链路：验签 → 风控 → 路由 → 渠道。</p>
     */
    @Test
    @Order(4)
    void step4_createPayment() throws Exception {
        outTradeNo = "TEST" + System.currentTimeMillis();

        // 构造支付请求体
        PayRequest payRequest = new PayRequest();
        payRequest.setOutTradeNo(outTradeNo);
        payRequest.setMerchantId(merchantId);
        payRequest.setAmount(new BigDecimal("99.99"));
        payRequest.setCurrency("CNY");
        payRequest.setNotifyUrl("https://test.payment.com/callback");
        payRequest.setSubject("测试商品");

        // 构建签名串
        String timestamp = String.valueOf(System.currentTimeMillis() / 1000);
        String nonce = IdUtil.fastSimpleUUID();
        String body = MAPPER.writeValueAsString(payRequest);
        String signContent = "POST" + "\n"
                + "/api/v1/pay/create" + "\n"
                + timestamp + "\n"
                + nonce + "\n"
                + body + "\n";

        // 用商户私钥签名
        String signature = RsaSignUtil.sign(signContent, privateKey);

        System.out.println("=== 支付请求 ===");
        System.out.println("outTradeNo: " + outTradeNo);
        System.out.println("merchantId: " + merchantId);
        System.out.println("amount: 99.99");
        System.out.println("signContent: " + signContent.substring(0, 60) + "...");
        System.out.println("signature: " + signature.substring(0, 40) + "...");

        // 发起支付请求
        String response = CLIENT.post()
                .uri(GATEWAY_URL + "/create")
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Signature", signature)
                .header("X-Timestamp", timestamp)
                .header("X-Nonce", nonce)
                .body(body)
                .retrieve()
                .body(String.class);

        System.out.println("=== 支付响应 ===");
        System.out.println(response);

        @SuppressWarnings("unchecked")
        Map<String, Object> result = MAPPER.readValue(response, Map.class);
        Map<String, Object> data = (Map<String, Object>) result.get("data");

        assertNotNull(data, "支付响应为空");
        assertEquals(outTradeNo, data.get("outTradeNo"));
        assertEquals(0, result.get("code"), "支付失败: " + result.get("message"));

        System.out.println("支付状态: " + data.get("payStatus"));
        System.out.println("渠道订单号: " + data.get("channelOrderNo"));
    }

    /**
     * 步骤 5：幂等性验证——重复同一订单号，应返回原结果。
     */
    @Test
    @Order(5)
    void step5_verifyIdempotency() throws Exception {
        // 构造同样的请求
        PayRequest payRequest = new PayRequest();
        payRequest.setOutTradeNo(outTradeNo);
        payRequest.setMerchantId(merchantId);
        payRequest.setAmount(new BigDecimal("99.99"));
        payRequest.setCurrency("CNY");
        payRequest.setNotifyUrl("https://test.payment.com/callback");
        payRequest.setSubject("测试商品");

        String timestamp = String.valueOf(System.currentTimeMillis() / 1000);
        String nonce = IdUtil.fastSimpleUUID();
        String body = MAPPER.writeValueAsString(payRequest);
        String signContent = "POST" + "\n"
                + "/api/v1/pay/create" + "\n"
                + timestamp + "\n"
                + nonce + "\n"
                + body + "\n";
        String signature = RsaSignUtil.sign(signContent, privateKey);

        String response = CLIENT.post()
                .uri(GATEWAY_URL + "/create")
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Signature", signature)
                .header("X-Timestamp", timestamp)
                .header("X-Nonce", nonce)
                .body(body)
                .retrieve()
                .body(String.class);

        System.out.println("=== 幂等验证响应 ===");
        System.out.println(response);

        @SuppressWarnings("unchecked")
        Map<String, Object> result = MAPPER.readValue(response, Map.class);

        // 幂等重复请求返回业务码 20001（DUPLICATE），不是 0
        assertEquals(20001, result.get("code"), "幂等校验失败：重复请求应返回 DUPLICATE");
        System.out.println("幂等验证通过：重复请求返回 " + result.get("message"));
    }
}
