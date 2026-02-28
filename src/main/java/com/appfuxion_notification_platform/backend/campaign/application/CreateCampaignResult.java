package com.appfuxion_notification_platform.backend.campaign.application;

import java.time.Instant;
import java.util.UUID;

import com.appfuxion_notification_platform.backend.campaign.domain.CampaignStatus;

public record CreateCampaignResult(
        UUID campaignId,
        UUID correlationId,
        CampaignStatus status,
        Instant acceptedAt,
        long totalRows,
        long acceptedRows,
        long invalidRows,
        boolean dispatchEnqueued) {
}
