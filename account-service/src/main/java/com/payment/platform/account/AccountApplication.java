package com.payment.platform.account;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * 账户服务启动类（核心模块）。
 * <p>负责商户账户的余额管理、TCC 分布式事务（Try/Confirm/Cancel）、复式记账流水。</p>
 */
@EnableDiscoveryClient
@SpringBootApplication(scanBasePackages = "com.payment.platform")
public class AccountApplication {

    public static void main(String[] args) {
        SpringApplication.run(AccountApplication.class, args);
    }
}
