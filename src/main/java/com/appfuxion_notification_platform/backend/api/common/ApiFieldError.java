package com.appfuxion_notification_platform.backend.api.common;

public record ApiFieldError(
        String field,
        String message,
        Object rejectedValue) {
}
