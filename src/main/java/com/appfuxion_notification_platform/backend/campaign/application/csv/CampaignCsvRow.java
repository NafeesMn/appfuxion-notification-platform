package com.appfuxion_notification_platform.backend.campaign.application.csv;

public record CampaignCsvRow(
        int rowNumber,
        String email,
        String phoneNumber,
        String deviceToken,
        String timezone,
        String personalizationPayloadJson) {
}
