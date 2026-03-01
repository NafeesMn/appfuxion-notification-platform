package com.appfuxion_notification_platform.backend.campaign.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.appfuxion_notification_platform.backend.campaign.application.csv.CampaignCsvRow;
import com.appfuxion_notification_platform.backend.domain.shared.NotificationChannel;

class DefaultRecipientRowValidatorTest {

    private final DefaultRecipientRowValidator validator = new DefaultRecipientRowValidator();

    @Test
    void validateAndNormalize_shouldNormalizeEmailAndApplyTenantTimezoneFallback() {
        CampaignCsvRow row = new CampaignCsvRow(
                1,
                "  Alice@Example.COM  ",
                null,
                null,
                null,
                null);

        RecipientRowValidationResult result = validator.validateAndNormalize(
                row,
                NotificationChannel.EMAIL,
                "Asia/Kuala_Lumpur");

        assertTrue(result.valid());
        assertEquals("alice@example.com", result.normalizedRecipientKey());
        assertEquals("alice@example.com", result.email());
        assertEquals("Asia/Kuala_Lumpur", result.effectiveTimezone());
    }

    @Test
    void validateAndNormalize_shouldNormalizePhoneDigitsForSms() {
        CampaignCsvRow row = new CampaignCsvRow(
                2,
                null,
                "(+60) 12-345 6789",
                null,
                "UTC",
                null);

        RecipientRowValidationResult result = validator.validateAndNormalize(
                row,
                NotificationChannel.SMS,
                "Asia/Kuala_Lumpur");

        assertTrue(result.valid());
        assertEquals("+60123456789", result.normalizedRecipientKey());
        assertEquals("+60123456789", result.phoneNumber());
        assertEquals("UTC", result.effectiveTimezone());
    }

    @Test
    void validateAndNormalize_shouldRejectTooShortPushDeviceToken() {
        CampaignCsvRow row = new CampaignCsvRow(
                3,
                null,
                null,
                "abc",
                "UTC",
                null);

        RecipientRowValidationResult result = validator.validateAndNormalize(
                row,
                NotificationChannel.PUSH,
                "UTC");

        assertFalse(result.valid());
        assertEquals("INVALID_DEVICE_TOKEN", result.errorCode());
    }

    @Test
    void validateAndNormalize_shouldRejectWhenTimezoneIsInvalidForRowAndTenantFallback() {
        CampaignCsvRow row = new CampaignCsvRow(
                4,
                "bob@example.com",
                null,
                null,
                "Mars/Phobos",
                null);

        RecipientRowValidationResult result = validator.validateAndNormalize(
                row,
                NotificationChannel.EMAIL,
                "Not/A_Timezone");

        assertFalse(result.valid());
        assertEquals("INVALID_TIMEZONE", result.errorCode());
    }
}
