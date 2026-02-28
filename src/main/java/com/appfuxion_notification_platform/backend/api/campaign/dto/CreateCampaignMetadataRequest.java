package com.appfuxion_notification_platform.backend.api.campaign.dto;

import com.appfuxion_notification_platform.backend.domain.shared.CampaignType;
import com.appfuxion_notification_platform.backend.domain.shared.NotificationChannel;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateCampaignMetadataRequest(
        @NotNull(message = "channel is required")
        NotificationChannel channel,

        @NotNull(message = "campaignType is required")
        CampaignType campaignType,

        @NotBlank(message = "messageTemplate is required")
        @Size(max = 10000, message = "messageTemplate must be at most 10000 characters")
        String messageTemplate,

        @Size(max = 128, message = "createdBy must be at most 128 characters")
        String createdBy) {
}
