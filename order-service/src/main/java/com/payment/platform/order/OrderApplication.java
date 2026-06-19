package com.payment.platform.order;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * 订单服务启动类。
 * <p>负责订单状态机管理、退款单处理，消费支付成功/退款事件。</p>
 */
@EnableDiscoveryClient
@SpringBootApplication(scanBasePackages = "com.payment.platform")
public class OrderApplication {

    public static void main(String[] args) {
        SpringApplication.run(OrderApplication.class, args);
    }
}
