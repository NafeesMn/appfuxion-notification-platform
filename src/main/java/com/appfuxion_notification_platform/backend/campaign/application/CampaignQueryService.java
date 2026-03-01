package com.appfuxion_notification_platform.backend.campaign.application;

import java.util.UUID;

import com.appfuxion_notification_platform.backend.api.campaign.dto.CampaignDetailResponse;
import com.appfuxion_notification_platform.backend.api.campaign.dto.CampaignListResponse;
import com.appfuxion_notification_platform.backend.campaign.domain.CampaignStatus;
import com.appfuxion_notification_platform.backend.domain.shared.NotificationChannel;

public interface CampaignQueryService {

    CampaignListResponse listCampaigns(
            String tenantKey,
            int page,
            int size,
            CampaignStatus status,
            NotificationChannel channel);

    CampaignDetailResponse getCampaign(String tenantKey, UUID campaignId);
}
