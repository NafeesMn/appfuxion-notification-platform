package com.appfuxion_notification_platform.backend.api.correlation;

import java.util.UUID;

import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class CorrelationContextInterceptor implements HandlerInterceptor {

    private final String correlationHeaderName;

    public CorrelationContextInterceptor(
            @Value("${app.correlation.header-name:X-Correlation-Id}") String correlationHeaderName) {
        this.correlationHeaderName = correlationHeaderName;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String incoming = request.getHeader(correlationHeaderName);
        String correlationId = StringUtils.hasText(incoming)
                ? incoming.trim()
                : UUID.randomUUID().toString();

        CorrelationContextHolder.set(correlationId);
        MDC.put("correlationId", correlationId);
        response.setHeader(correlationHeaderName, correlationId);
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        MDC.remove("correlationId");
        CorrelationContextHolder.clear();
    }
}
