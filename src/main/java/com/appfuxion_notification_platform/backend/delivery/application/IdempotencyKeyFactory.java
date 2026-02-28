package com.appfuxion_notification_platform.backend.delivery.application;

import java.util.UUID;

import com.appfuxion_notification_platform.backend.domain.shared.NotificationChannel;

public interface IdempotencyKeyFactory {

    String build(
            UUID tenantId,
            UUID campaignId,
            UUID campaignRecipientId,
            NotificationChannel channel,
            String normalizedMessageHash);
}
