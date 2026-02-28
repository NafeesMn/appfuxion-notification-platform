package com.appfuxion_notification_platform.backend.api.campaign.dto;

public record CampaignDeliverySummaryResponse(
        long total,
        long sent,
        long failed,
        long skipped,
        long delayed,
        long pending) {
}
