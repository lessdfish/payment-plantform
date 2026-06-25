package com.payment.platform.gateway;

import cn.hutool.core.util.IdUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.payment.platform.common.dto.request.PayRequest;
import com.payment.platform.common.util.RsaSignUtil;
import org.junit.jupiter.api.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 风控验收测试。
 * <p>运行前需启动：merchant(8085), simulator(8086), account(8081), gateway(8080)</p>
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class RiskControlTest {

    private static final RestClient CLIENT = RestClient.builder()
            .requestFactory(new SimpleClientHttpRequestFactory())
            .build();
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String GATEWAY_URL = "http://localhost:8080/api/v1/pay";
    private static final String ACCOUNT_URL = "http://localhost:8081/api/v1/account";
    private static final String TEST_IP = "10.44.0." + (System.currentTimeMillis() % 200 + 1);
    private static Long merchantId;
    private static String publicKey;
    private static String privateKey;

    @Test
    @Order(1)
    void tc01_registerAndSetup() throws Exception {
        // 注册商户
        String regResp = CLIENT.post()
                .uri("http://localhost:8085/api/v1/merchant/register")
                .header("Content-Type", "application/json")
                .body("{\"merchantName\":\"风控测试商户\",\"contactEmail\":\"risk@test.com\"}")
                .retrieve().body(String.class);
        @SuppressWarnings("unchecked")
        var result = (java.util.Map<String, Object>) MAPPER.readValue(regResp, java.util.Map.class);
        var data = (java.util.Map<String, Object>) result.get("data");
        merchantId = Long.valueOf(data.get("id").toString());

        // 生成密钥
        String keyResp = CLIENT.post()
                .uri("http://localhost:8085/api/v1/merchant/" + merchantId + "/key/generate")
                .retrieve().body(String.class);
        var keyData = (java.util.Map<String, Object>) MAPPER.readValue(keyResp, java.util.Map.class)
                .get("data");
        publicKey = (String) ((java.util.Map<String, Object>) keyData).get("publicKey");
        privateKey = (String) ((java.util.Map<String, Object>) keyData).get("privateKey");

        // 配置费率
        CLIENT.post()
                .uri("http://localhost:8085/api/v1/merchant/" + merchantId + "/rate")
                .header("Content-Type", "application/json")
                .body("{\"channelType\":\"WECHAT\",\"feeRate\":0.0038}")
                .retrieve().toBodilessEntity();

        CLIENT.post()
                .uri(ACCOUNT_URL + "/recharge/{merchantId}?amount=10000.00&outTradeNo={outTradeNo}",
                        merchantId, "RISK_RECHARGE_" + System.currentTimeMillis())
                .retrieve().toBodilessEntity();

        System.out.println("TC01 准备完成: merchantId=" + merchantId);
        assertNotNull(merchantId);
    }

    @Test
    @Order(2)
    void tc02_paymentPassRiskCheck() throws Exception {
        String outTradeNo = "RISK_OK" + System.currentTimeMillis();
        String response = makePayment(outTradeNo, new BigDecimal("99.99"));
        System.out.println("TC02 正常支付: " + response);
        assertFalse(response.contains("单笔交易金额超过上限")
                || response.contains("商户已被风控拦截")
                || response.contains("请求过于频繁"), "正常支付不应被风控拦截");
    }

    @Test
    @Order(3)
    void tc03_amountExceedsSingleLimit() throws Exception {
        String outTradeNo = "RISK_OVER" + System.currentTimeMillis();
        String response = makePayment(outTradeNo, new BigDecimal("60000.00"));
        System.out.println("TC03 单笔超限: " + response);
        assertTrue(response.contains("50001") || response.contains("超额") || response.contains("422"),
                "单笔超限应被拦截");
    }

    @Test
    @Order(4)
    void tc04_ipRateLimit() throws Exception {
        System.out.println("TC04 IP 频控：连续发送 60 次请求...");
        int rejected = 0;
        for (int i = 0; i < 60; i++) {
            String outTradeNo = "RISK_IP" + System.currentTimeMillis() + "_" + i;
            String response = makePayment(outTradeNo, new BigDecimal("60000.00"));
            if (response.contains("429") || response.contains("频繁")) {
                rejected++;
            }
            if (i % 20 == 0) System.out.println("  已发送 " + i + " 次, 被拒 " + rejected + " 次");
        }
        System.out.println("TC04 结果: 被拒 " + rejected + " 次");
        assertTrue(rejected > 0, "应至少有 1 次被 IP 频控拦截");
    }

    private String makePayment(String outTradeNo, BigDecimal amount) throws Exception {
        PayRequest req = new PayRequest();
        req.setOutTradeNo(outTradeNo);
        req.setMerchantId(merchantId);
        req.setAmount(amount);
        req.setCurrency("CNY");
        req.setNotifyUrl("https://test.com/callback");
        req.setSubject("风控测试");

        String ts = String.valueOf(System.currentTimeMillis() / 1000);
        String nonce = IdUtil.fastSimpleUUID();
        String body = MAPPER.writeValueAsString(req);
        String signContent = "POST\n/api/v1/pay/create\n" + ts + "\n" + nonce + "\n" + body + "\n";
        String sig = RsaSignUtil.sign(signContent, privateKey);

        try {
            return CLIENT.post()
                    .uri(GATEWAY_URL + "/create")
                    .header("Content-Type", "application/json")
                    .header("X-Signature", sig)
                    .header("X-Timestamp", ts)
                    .header("X-Nonce", nonce)
                    .header("X-Forwarded-For", TEST_IP)
                    .body(body)
                    .retrieve()
                    .body(String.class);
        } catch (RestClientResponseException e) {
            return e.getResponseBodyAsString();
        }
    }
}
