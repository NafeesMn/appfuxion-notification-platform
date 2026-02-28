package com.appfuxion_notification_platform.backend.campaign.application;

import com.appfuxion_notification_platform.backend.campaign.application.csv.CampaignCsvRow;
import com.appfuxion_notification_platform.backend.domain.shared.NotificationChannel;

public interface RecipientRowValidator {

    RecipientRowValidationResult validateAndNormalize(
            CampaignCsvRow row,
            NotificationChannel channel,
            String tenantDefaultTimezone);
}
