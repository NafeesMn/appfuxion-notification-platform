package com.appfuxion_notification_platform.backend.api.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import com.appfuxion_notification_platform.backend.api.correlation.CorrelationContextInterceptor;
import com.appfuxion_notification_platform.backend.api.tenant.TenantContextInterceptor;

@Configuration
public class WebTenantContextConfig implements WebMvcConfigurer {

    private final CorrelationContextInterceptor correlationContextInterceptor;
    private final TenantContextInterceptor tenantContextInterceptor;
    private final String[] allowedOrigins;
    private final String[] allowedMethods;
    private final String[] allowedHeaders;
    private final String[] exposedHeaders;
    private final boolean allowCredentials;
    private final long maxAgeSeconds;

    public WebTenantContextConfig(
            CorrelationContextInterceptor correlationContextInterceptor,
            TenantContextInterceptor tenantContextInterceptor,
            @Value("${app.cors.allowed-origins:http://localhost:5173,http://127.0.0.1:5173}") String[] allowedOrigins,
            @Value("${app.cors.allowed-methods:GET,POST,PUT,PATCH,DELETE,OPTIONS}") String[] allowedMethods,
            @Value("${app.cors.allowed-headers:Content-Type,Authorization,X-Tenant-Key,X-Correlation-Id}") String[] allowedHeaders,
            @Value("${app.cors.exposed-headers:X-Correlation-Id}") String[] exposedHeaders,
            @Value("${app.cors.allow-credentials:true}") boolean allowCredentials,
            @Value("${app.cors.max-age-seconds:3600}") long maxAgeSeconds) {
        this.correlationContextInterceptor = correlationContextInterceptor;
        this.tenantContextInterceptor = tenantContextInterceptor;
        this.allowedOrigins = allowedOrigins;
        this.allowedMethods = allowedMethods;
        this.allowedHeaders = allowedHeaders;
        this.exposedHeaders = exposedHeaders;
        this.allowCredentials = allowCredentials;
        this.maxAgeSeconds = maxAgeSeconds;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(correlationContextInterceptor).addPathPatterns("/**");
        registry.addInterceptor(tenantContextInterceptor)
                .addPathPatterns("/campaigns", "/campaigns/**");
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOrigins(allowedOrigins)
                .allowedMethods(allowedMethods)
                .allowedHeaders(allowedHeaders)
                .exposedHeaders(exposedHeaders)
                .allowCredentials(allowCredentials)
                .maxAge(maxAgeSeconds);
    }
}
