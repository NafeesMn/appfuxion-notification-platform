package com.appfuxion_notification_platform.backend.api.campaign.dto;

import java.util.List;

public record CampaignListResponse(
        int page,
        int size,
        long totalElements,
        int totalPages,
        List<CampaignSummaryResponse> items) {
}
