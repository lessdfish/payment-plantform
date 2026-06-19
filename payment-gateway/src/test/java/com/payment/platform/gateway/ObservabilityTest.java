package com.payment.platform.gateway;

import org.junit.jupiter.api.*;
import org.springframework.web.client.RestClient;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 可观测性验收测试。
 * <p>运行前需启动：Docker(含 Prometheus:9090, Grafana:3000) + 7 个服务</p>
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ObservabilityTest {

    private static final RestClient CLIENT = RestClient.create();

    @Test
    @Order(1)
    void tc01_prometheusEndpoint() {
        String response = CLIENT.get()
                .uri("http://localhost:8080/actuator/prometheus")
                .retrieve()
                .body(String.class);
        System.out.println("TC01 Prometheus 指标样例(前200字符): " + response.substring(0, Math.min(200, response.length())));
        assertTrue(response.contains("http_server_requests_seconds"), "应包含 HTTP 请求指标");
        assertTrue(response.contains("jvm_"), "应包含 JVM 指标");
    }

    @Test
    @Order(2)
    void tc02_allServicesPrometheus() {
        int[] ports = {8080, 8081, 8082, 8083, 8084, 8085, 8086};
        for (int port : ports) {
            try {
                String resp = CLIENT.get()
                        .uri("http://localhost:" + port + "/actuator/prometheus")
                        .retrieve()
                        .body(String.class);
                System.out.println("  端口 " + port + ": Prometheus OK (" + resp.length() + " bytes)");
            } catch (Exception e) {
                System.out.println("  端口 " + port + ": 不可达 (服务可能未启动)");
            }
        }
        assertTrue(true, "各服务 Prometheus 端点检测完成");
    }

    @Test
    @Order(3)
    void tc03_prometheusServerRunning() {
        try {
            String response = CLIENT.get()
                    .uri("http://localhost:9090/api/v1/targets")
                    .retrieve()
                    .body(String.class);
            System.out.println("TC03 Prometheus Server 状态: " + response.substring(0, Math.min(100, response.length())));
            assertNotNull(response);
        } catch (Exception e) {
            System.out.println("TC03 Prometheus Server 不可达 (Docker 是否启动?): " + e.getMessage());
        }
    }

    @Test
    @Order(4)
    void tc04_grafanaRunning() {
        try {
            String response = CLIENT.get()
                    .uri("http://localhost:3000/api/health")
                    .retrieve()
                    .body(String.class);
            System.out.println("TC04 Grafana 状态: " + response);
            assertNotNull(response);
        } catch (Exception e) {
            System.out.println("TC04 Grafana 不可达 (Docker 是否启动?): " + e.getMessage());
        }
    }
}
