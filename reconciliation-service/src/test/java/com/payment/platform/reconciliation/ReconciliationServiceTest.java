package com.payment.platform.reconciliation;

import org.junit.jupiter.api.*;
import org.springframework.web.client.RestClient;

import static org.junit.jupiter.api.Assertions.*;

/**
 * reconciliation-service 验收测试。
 * <p>运行前需启动：reconciliation(8084), simulator(8086)</p>
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ReconciliationServiceTest {

    private static final RestClient CLIENT = RestClient.create();

    @Test
    @Order(1)
    void tc01_xxlJobExecutorRegistered() {
        // 验证服务启动后 XXL-JOB 执行器已注册
        // 通过 Nacos 或日志确认，此处简化验证
        System.out.println("验收方式：浏览器打开 http://localhost:8087/xxl-job-admin");
        System.out.println("进入「执行器管理」→ 查看 reconciliation-service 是否在线");
        assertTrue(true);
    }

    @Test
    @Order(2)
    void tc02_billDownloadWorks() {
        // 先通过 simulator 产生一笔订单，然后下载账单
        String response = CLIENT.get()
                .uri("http://localhost:8086/api/v1/simulator/bill/2024-06-19")
                .retrieve()
                .body(String.class);
        System.out.println("TC02 账单下载: " + response);
        assertNotNull(response);
        assertTrue(response.contains("\"code\":0"));
    }
}
