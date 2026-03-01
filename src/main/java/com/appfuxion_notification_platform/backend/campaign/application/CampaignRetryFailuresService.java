package com.appfuxion_notification_platform.backend.campaign.application;

import java.util.UUID;

import com.appfuxion_notification_platform.backend.api.campaign.dto.RetryFailuresAcceptedResponse;

public interface CampaignRetryFailuresService {

    RetryFailuresAcceptedResponse retryFailures(String tenantKey, UUID campaignId);
}
