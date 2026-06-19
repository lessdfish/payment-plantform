package com.payment.platform.reconciliation.config;

import com.xxl.job.core.executor.impl.XxlJobSpringExecutor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * XXL-JOB 执行器配置。
 */
@Slf4j
@Configuration
public class XxlJobConfig {

    @Value("${xxl.job.admin.addresses:http://localhost:8087/xxl-job-admin}")
    private String adminAddresses;

    @Value("${xxl.job.executor.appname:reconciliation-service}")
    private String appName;

    @Bean
    public XxlJobSpringExecutor xxlJobSpringExecutor() {
        XxlJobSpringExecutor executor = new XxlJobSpringExecutor();
        executor.setAdminAddresses(adminAddresses);
        executor.setAppname(appName);
        executor.setPort(9998);
        log.info("[XXL-JOB] 执行器注册: appName={}, adminUrl={}", appName, adminAddresses);
        return executor;
    }
}
