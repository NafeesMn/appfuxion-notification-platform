package com.appfuxion_notification_platform.backend.api.tenant;

import java.util.Optional;

public final class TenantContextHolder {

    private static final ThreadLocal<TenantContext> CONTEXT = new ThreadLocal<>();

    private TenantContextHolder() {
    }

    public static void set(TenantContext tenantContext) {
        CONTEXT.set(tenantContext);
    }

    public static Optional<TenantContext> get() {
        return Optional.ofNullable(CONTEXT.get());
    }

    public static String requireTenantKey() {
        return get()
                .map(TenantContext::tenantKey)
                .orElseThrow(() -> new MissingTenantContextException("X-Tenant-Key"));
    }

    public static void clear() {
        CONTEXT.remove();
    }
}
