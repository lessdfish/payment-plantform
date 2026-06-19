package com.payment.platform.notification;

import org.junit.jupiter.api.*;
import org.springframework.web.client.RestClient;

import static org.junit.jupiter.api.Assertions.*;

/**
 * notification-service 验收测试。
 * <p>运行前需启动：merchant(8085), simulator(8086), account(8081), gateway(8080), notify(8083)</p>
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class NotificationServiceTest {

    private static final RestClient CLIENT = RestClient.create();
    private static final String BASE_URL = "http://localhost:8083/api/v1/notification";

    @Test
    @Order(1)
    void tc01_serviceIsRunning() {
        String response = CLIENT.get()
                .uri(BASE_URL + "/record/TEST_NONEXIST")
                .retrieve()
                .body(String.class);
        System.out.println("TC01 服务启动检测: " + response);
        assertNotNull(response, "notification-service 未启动");
    }
}
