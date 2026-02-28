package com.appfuxion_notification_platform.backend.api.campaign.dto;

import java.time.Instant;
import java.util.UUID;

import com.appfuxion_notification_platform.backend.campaign.domain.CampaignStatus;
import com.appfuxion_notification_platform.backend.domain.shared.CampaignType;
import com.appfuxion_notification_platform.backend.domain.shared.NotificationChannel;

public record CampaignDetailResponse(
        UUID id,
        UUID correlationId,
        NotificationChannel channel,
        CampaignType campaignType,
        CampaignStatus status,
        String messageTemplate,
        String createdBy,
        Instant createdAt,
        Instant updatedAt,
        CampaignImportSummaryResponse importSummary,
        CampaignDeliverySummaryResponse deliverySummary) {
}
