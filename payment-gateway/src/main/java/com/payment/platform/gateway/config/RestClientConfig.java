package com.payment.platform.gateway.config;

import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.core5.util.Timeout;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * 内部服务调用使用的共享 HTTP 连接池。
 */
@Configuration
public class RestClientConfig {

    @Bean(destroyMethod = "close")
    public CloseableHttpClient internalHttpClient() {
        var connectionManager = PoolingHttpClientConnectionManagerBuilder.create()
                .setMaxConnTotal(1_000)
                .setMaxConnPerRoute(500)
                .build();
        var requestConfig = RequestConfig.custom()
                .setConnectTimeout(Timeout.ofSeconds(2))
                .setConnectionRequestTimeout(Timeout.ofSeconds(2))
                .setResponseTimeout(Timeout.ofSeconds(30))
                .build();
        return HttpClients.custom()
                .setConnectionManager(connectionManager)
                .setDefaultRequestConfig(requestConfig)
                .evictExpiredConnections()
                .build();
    }

    @Bean
    public RestClient internalRestClient(CloseableHttpClient internalHttpClient) {
        return RestClient.builder()
                .requestFactory(
                        new HttpComponentsClientHttpRequestFactory(internalHttpClient))
                .build();
    }
}
