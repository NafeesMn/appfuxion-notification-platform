package com.appfuxion_notification_platform.backend.api.tenant;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
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
        String tenantKey = request.getHeader(tenantHeaderName);
        if (!StringUtils.hasText(tenantKey)) {
            throw new MissingTenantContextException(tenantHeaderName);
        }

        TenantContextHolder.set(new TenantContext(tenantKey.trim()));
        return true;
    }

    @Override
    public void afterCompletion(
            HttpServletRequest request,
            HttpServletResponse response,
            Object handler,
            Exception ex) {
        TenantContextHolder.clear();
    }
}
