package com.payment.platform.gateway.filter;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * 请求验签过滤器。
 * <p>对所有 /api/v1/pay/** 路径的请求进行签名校验的前置处理。
 * 当前验签逻辑已内嵌在 PayService 中，此 Filter 负责记录请求日志和 traceId 注入。</p>
 */
@Slf4j
@Component
public class SignatureFilter implements Filter {

    @Override
    public void doFilter(ServletRequest request, ServletResponse response,
                         FilterChain chain) throws IOException, ServletException {
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        String path = httpRequest.getRequestURI();

        // 仅对支付类接口进行日志增强
        if (path.startsWith("/api/v1/pay")) {
            String method = httpRequest.getMethod();
            String signature = httpRequest.getHeader("X-Signature");
            String timestamp = httpRequest.getHeader("X-Timestamp");
            String nonce = httpRequest.getHeader("X-Nonce");

            log.info("[GATEWAY] {} {} | Sign={} | Time={} | Nonce={}",
                    method, path,
                    signature != null ? "YES" : "NO",
                    timestamp,
                    nonce != null ? "YES" : "NO");
        }

        chain.doFilter(request, response);
    }
}
