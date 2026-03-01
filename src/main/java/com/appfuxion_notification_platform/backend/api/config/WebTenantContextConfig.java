package com.appfuxion_notification_platform.backend.api.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import com.appfuxion_notification_platform.backend.api.correlation.CorrelationContextInterceptor;
import com.appfuxion_notification_platform.backend.api.tenant.TenantContextInterceptor;

@Configuration
public class WebTenantContextConfig implements WebMvcConfigurer {

    private final CorrelationContextInterceptor correlationContextInterceptor;
    private final TenantContextInterceptor tenantContextInterceptor;

    public WebTenantContextConfig(
            CorrelationContextInterceptor correlationContextInterceptor,
            TenantContextInterceptor tenantContextInterceptor) {
        this.correlationContextInterceptor = correlationContextInterceptor;
        this.tenantContextInterceptor = tenantContextInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(correlationContextInterceptor).addPathPatterns("/**");
        registry.addInterceptor(tenantContextInterceptor)
                .addPathPatterns("/campaigns", "/campaigns/**");
    }
}
