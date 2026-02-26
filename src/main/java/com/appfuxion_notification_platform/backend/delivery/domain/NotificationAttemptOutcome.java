package com.appfuxion_notification_platform.backend.delivery.domain;

public enum NotificationAttemptOutcome {
    SUCCESS,
    RETRYABLE_FAILURE,
    TERMINAL_FAILURE,
    THROTTLED,
    RULE_DELAYED,
    RULE_SKIPPED
}
