package com.payment.platform.notification;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * 通知服务启动类。
 * <p>负责向商户发送 HTTP 回调通知，失败时执行退避重试策略。</p>
 */
@EnableDiscoveryClient
@SpringBootApplication(scanBasePackages = "com.payment.platform")
public class NotificationApplication {

    public static void main(String[] args) {
        SpringApplication.run(NotificationApplication.class, args);
    }
}
