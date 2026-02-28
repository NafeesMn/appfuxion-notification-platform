package com.appfuxion_notification_platform.backend.api.common;

import java.time.Instant;
import java.util.List;

public record ApiErrorResponse(
        Instant timestamp,
        int status,
        String error,
        String message,
        String path,
        String tenantKey,
        List<ApiFieldError> fieldErrors) {
}
