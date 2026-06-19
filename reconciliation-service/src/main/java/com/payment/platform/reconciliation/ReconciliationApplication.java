package com.payment.platform.reconciliation;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * 对账服务启动类。
 * <p>负责 Canal 实时对账 + XXL-JOB 批处理对账，发现并处理交易差异。</p>
 */
@EnableDiscoveryClient
@SpringBootApplication(scanBasePackages = "com.payment.platform")
public class ReconciliationApplication {

    public static void main(String[] args) {
        SpringApplication.run(ReconciliationApplication.class, args);
    }
}
