package com.appfuxion_notification_platform.backend.api.tenant;

import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.cors.CorsUtils;
import org.springframework.web.servlet.HandlerInterceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class TenantContextInterceptor implements HandlerInterceptor {

    private final String tenantHeaderName;

    public TenantContextInterceptor(
            @Value("${app.tenant.header-name:X-Tenant-Key}") String tenantHeaderName) {
        this.tenantHeaderName = tenantHeaderName;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (CorsUtils.isPreFlightRequest(request)) {
            return true;
        }

        String tenantKey = request.getHeader(tenantHeaderName);
        if (!StringUtils.hasText(tenantKey)) {
            throw new MissingTenantContextException(tenantHeaderName);
        }

        String normalizedTenantKey = tenantKey.trim();
        TenantContextHolder.set(new TenantContext(normalizedTenantKey));
        MDC.put("tenantKey", normalizedTenantKey);
        return true;
    }

    @Override
    public void afterCompletion(
            HttpServletRequest request,
            HttpServletResponse response,
            Object handler,
            Exception ex) {
        MDC.remove("tenantKey");
        TenantContextHolder.clear();
    }
}
