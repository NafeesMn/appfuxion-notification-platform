package com.appfuxion_notification_platform.backend.api.campaign.dto;

import java.time.Instant;
import java.util.UUID;

public record CampaignCreateAcceptedResponse(
        UUID campaignId,
        UUID correlationId,
        String status,
        Instant acceptedAt,
        CampaignImportSummaryResponse importSummary) {
}
