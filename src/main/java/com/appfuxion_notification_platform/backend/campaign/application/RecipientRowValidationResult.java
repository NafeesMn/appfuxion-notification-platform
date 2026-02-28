package com.appfuxion_notification_platform.backend.campaign.application;

public record RecipientRowValidationResult(
        boolean valid,
        String normalizedRecipientKey,
        String email,
        String phoneNumber,
        String deviceToken,
        String effectiveTimezone,
        String errorCode,
        String errorMessage) {
}
