package com.payment.platform.simulator;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * 渠道模拟器启动类。
 * <p>模拟微信/支付宝/银联的支付接口、查单接口、账单下载接口。
 * 支持可配置的成功率、延迟和异常模式，用于端到端测试和压测。</p>
 */
@EnableDiscoveryClient
@SpringBootApplication(scanBasePackages = "com.payment.platform")
public class SimulatorApplication {

    public static void main(String[] args) {
        SpringApplication.run(SimulatorApplication.class, args);
    }
}
