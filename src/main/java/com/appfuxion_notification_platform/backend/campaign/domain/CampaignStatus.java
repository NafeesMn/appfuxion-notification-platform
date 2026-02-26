package com.appfuxion_notification_platform.backend.campaign.domain;

public enum CampaignStatus {
    INGESTING,
    READY_FOR_DISPATCH,
    PROCESSING,
    COMPLETED,
    COMPLETED_WITH_FAILURES,
    FAILED_IMPORT
}
