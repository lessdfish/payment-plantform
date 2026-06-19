package com.payment.platform.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * 支付网关服务启动类。
 * <p>负责商户请求的统一入口：签名验签、渠道路由、幂等校验、风控拦截。</p>
 */
@EnableDiscoveryClient
@SpringBootApplication(scanBasePackages = "com.payment.platform")
public class GatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(GatewayApplication.class, args);
    }
}
