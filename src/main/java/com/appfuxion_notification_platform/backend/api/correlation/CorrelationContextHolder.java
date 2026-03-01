package com.appfuxion_notification_platform.backend.api.correlation;

import java.util.Optional;

public final class CorrelationContextHolder {

    private static final ThreadLocal<String> CONTEXT = new ThreadLocal<>();

    private CorrelationContextHolder() {
    }

    public static void set(String correlationId) {
        CONTEXT.set(correlationId);
    }

    public static Optional<String> get() {
        return Optional.ofNullable(CONTEXT.get());
    }

    public static String requireCorrelationId() {
        return get().orElseThrow(() -> new IllegalStateException("Missing correlation context"));
    }

    public static void clear() {
        CONTEXT.remove();
    }
}
