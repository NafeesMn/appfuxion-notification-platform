package com.appfuxion_notification_platform.backend.api.campaign.dto;

import java.time.Instant;
import java.util.UUID;

import com.appfuxion_notification_platform.backend.campaign.domain.CampaignStatus;
import com.appfuxion_notification_platform.backend.domain.shared.CampaignType;
import com.appfuxion_notification_platform.backend.domain.shared.NotificationChannel;

public record CampaignSummaryResponse(
        UUID id,
        NotificationChannel channel,
        CampaignType campaignType,
        CampaignStatus status,
        Instant createdAt,
        CampaignDeliverySummaryResponse deliverySummary) {
}
