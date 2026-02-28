package com.appfuxion_notification_platform.backend.api.campaign.dto;

public record CampaignImportSummaryResponse(
        long totalRows,
        long acceptedRows,
        long invalidRows) {
}
