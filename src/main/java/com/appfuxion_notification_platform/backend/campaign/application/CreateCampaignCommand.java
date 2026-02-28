package com.appfuxion_notification_platform.backend.campaign.application;

import java.io.InputStream;
import java.util.UUID;

import com.appfuxion_notification_platform.backend.domain.shared.CampaignType;
import com.appfuxion_notification_platform.backend.domain.shared.NotificationChannel;

public record CreateCampaignCommand(
        String tenantKey,
        UUID correlationId,
        NotificationChannel channel,
        CampaignType campaignType,
        String messageTemplate,
        String createdBy,
        String originalFilename,
        InputStream csvInputStream) {
}
