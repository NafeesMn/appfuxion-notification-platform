package com.appfuxion_notification_platform.backend.api.campaign.dto;

import java.time.Instant;
import java.util.UUID;

public record RetryFailuresAcceptedResponse(
        UUID campaignId,
        String status,
        Instant requestedAt) {
}
