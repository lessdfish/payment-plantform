package com.payment.platform.account;

import com.payment.platform.common.dto.request.CancelRequest;
import com.payment.platform.common.dto.request.ConfirmRequest;
import com.payment.platform.common.dto.request.TryRequest;
import org.junit.jupiter.api.*;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 账户服务集成测试。
 * <p><b>运行前需确保：</b></p>
 * <ul>
 *   <li>Docker 中间件已启动（MySQL ds0~ds3, Nacos）</li>
 *   <li>account-service 已启动（端口 8081）</li>
 * </ul>
 * <p>运行方式：IDEA 右键 → Run 'AccountServiceTest'</p>
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AccountServiceTest {

    private static final RestClient CLIENT = RestClient.create();
    private static final String BASE_URL = "http://localhost:8081/api/v1/account";

    private static final Long MERCHANT_ID = 999001L;
    private static final Long MERCHANT_ID_2 = 999002L;
    private static String tccId;

    /**
     * TC01：创建账户（通过充值自动创建）。
     */
    @Test
    @Order(1)
    void tc01_rechargeCreatesAccount() {
        String outTradeNo = "RCH" + System.currentTimeMillis();
        String response = CLIENT.post()
                .uri(BASE_URL + "/recharge/{merchantId}?amount=10000.00&outTradeNo={outTradeNo}",
                        MERCHANT_ID, outTradeNo)
                .retrieve()
                .body(String.class);
        System.out.println("TC01 充值响应: " + response);
        assertNotNull(response, "充值失败");
    }

    /**
     * TC02：查询余额。
     */
    @Test
    @Order(2)
    void tc02_getBalance() {
        String response = CLIENT.get()
                .uri(BASE_URL + "/balance/{merchantId}", MERCHANT_ID)
                .retrieve()
                .body(String.class);
        System.out.println("TC02 余额查询: " + response);
        assertTrue(response.contains("10000.00"), "余额应为 10000");
    }

    /**
     * TC03：TCC Try 冻结。
     */
    @Test
    @Order(3)
    void tc03_tryFreeze() {
        TryRequest req = new TryRequest();
        req.setMerchantId(MERCHANT_ID);
        req.setAmount(new BigDecimal("500.00"));
        req.setBizOrderNo("BIZ" + System.currentTimeMillis());

        String response = CLIENT.post()
                .uri(BASE_URL + "/tcc/try")
                .body(req)
                .retrieve()
                .body(String.class);
        System.out.println("TC03 Try 响应: " + response);
        assertTrue(response.contains("\"code\":0"), "Try 应成功");

        // 提取 tccId（简单字符串解析）
        tccId = response.split("\"tccId\":\"")[1].split("\"")[0];
        System.out.println("tccId: " + tccId);
        assertNotNull(tccId, "应返回 tccId");
    }

    /**
     * TC04：TCC Confirm 实扣 + 复式流水。
     */
    @Test
    @Order(4)
    void tc04_confirm() {
        assertNotNull(tccId, "需要先执行 Try");

        ConfirmRequest req = new ConfirmRequest();
        req.setTccId(tccId);
        String response = CLIENT.post()
                .uri(BASE_URL + "/tcc/confirm")
                .body(req)
                .retrieve()
                .body(String.class);
        System.out.println("TC04 Confirm 响应: " + response);
        assertTrue(response.contains("\"code\":0"), "Confirm 应成功");

        // 验证余额减少
        String balanceResp = CLIENT.get()
                .uri(BASE_URL + "/balance/{merchantId}", MERCHANT_ID)
                .retrieve()
                .body(String.class);
        System.out.println("TC04 余额: " + balanceResp);
        assertTrue(balanceResp.contains("9500.00"), "余额应为 9500（10000-500）");
    }

    /**
     * TC05：TCC Cancel 释放冻结。
     */
    @Test
    @Order(5)
    void tc05_cancel() {
        // 先 Try
        TryRequest tryReq = new TryRequest();
        tryReq.setMerchantId(MERCHANT_ID_2);
        tryReq.setAmount(new BigDecimal("300.00"));
        tryReq.setBizOrderNo("BIZ_CANCEL" + System.currentTimeMillis());

        // 先充值到 MERCHANT_ID_2
        CLIENT.post()
                .uri(BASE_URL + "/recharge/{merchantId}?amount=1000.00&outTradeNo=RCH_CANCEL"
                        + System.currentTimeMillis(), MERCHANT_ID_2)
                .retrieve().toBodilessEntity();

        String tryResp = CLIENT.post()
                .uri(BASE_URL + "/tcc/try")
                .body(tryReq)
                .retrieve()
                .body(String.class);
        String cancelTccId = tryResp.split("\"tccId\":\"")[1].split("\"")[0];

        // Cancel
        CancelRequest cancelReq = new CancelRequest();
        cancelReq.setTccId(cancelTccId);
        String response = CLIENT.post()
                .uri(BASE_URL + "/tcc/cancel")
                .body(cancelReq)
                .retrieve()
                .body(String.class);
        System.out.println("TC05 Cancel 响应: " + response);
        assertTrue(response.contains("\"code\":0"), "Cancel 应成功");

        // 验证余额不变（1000，冻结已释放）
        String balanceResp = CLIENT.get()
                .uri(BASE_URL + "/balance/{merchantId}", MERCHANT_ID_2)
                .retrieve()
                .body(String.class);
        System.out.println("TC05 余额: " + balanceResp);
        assertTrue(balanceResp.contains("\"balance\":1000.00"), "Cancel 后余额应恢复");
    }

    /**
     * TC06：重复 Confirm 幂等。
     */
    @Test
    @Order(6)
    void tc06_confirmIdempotency() {
        assertNotNull(tccId, "需要先执行 Try");
        ConfirmRequest req = new ConfirmRequest();
        req.setTccId(tccId);
        // 第二次 Confirm
        String response = CLIENT.post()
                .uri(BASE_URL + "/tcc/confirm")
                .body(req)
                .retrieve()
                .body(String.class);
        System.out.println("TC06 重复Confirm: " + response);
        assertTrue(response.contains("\"code\":0"), "幂等 Confirm 应成功");

        // 余额不应该再变（还是 9500）
        String balanceResp = CLIENT.get()
                .uri(BASE_URL + "/balance/{merchantId}", MERCHANT_ID)
                .retrieve()
                .body(String.class);
        System.out.println("TC06 余额: " + balanceResp);
        assertTrue(balanceResp.contains("9500.00"), "幂等 Confirm 后余额不应再变");
    }

    /**
     * TC07：余额不足时 Try 应失败。
     */
    @Test
    @Order(7)
    void tc07_tryInsufficient() {
        TryRequest req = new TryRequest();
        req.setMerchantId(MERCHANT_ID);
        req.setAmount(new BigDecimal("999999.00")); // 远超可用余额
        req.setBizOrderNo("BIZ_FAIL" + System.currentTimeMillis());

        String response = CLIENT.post()
                .uri(BASE_URL + "/tcc/try")
                .body(req)
                .retrieve()
                .body(String.class);
        System.out.println("TC07 余额不足: " + response);
        assertTrue(response.contains("42201"), "余额不足应返回 42201");
    }
}
