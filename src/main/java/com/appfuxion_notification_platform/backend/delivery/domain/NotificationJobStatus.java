package com.appfuxion_notification_platform.backend.delivery.domain;

public enum NotificationJobStatus {
    PENDING,
    PROCESSING,
    RETRY_SCHEDULED,
    DELAYED,
    SENT,
    FAILED,
    SKIPPED
}
