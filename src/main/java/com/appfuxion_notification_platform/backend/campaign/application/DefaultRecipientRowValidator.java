package com.appfuxion_notification_platform.backend.campaign.application;

import java.time.ZoneId;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import com.appfuxion_notification_platform.backend.campaign.application.csv.CampaignCsvRow;
import com.appfuxion_notification_platform.backend.domain.shared.NotificationChannel;

@Component
public class DefaultRecipientRowValidator implements RecipientRowValidator {

    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");
    private static final Pattern NON_DIGIT_PATTERN = Pattern.compile("\\D+");

    @Override
    public RecipientRowValidationResult validateAndNormalize(
            CampaignCsvRow row,
            NotificationChannel channel,
            String tenantDefaultTimezone) {
        Objects.requireNonNull(row, "row");
        Objects.requireNonNull(channel, "channel");

        String timezone = resolveTimezone(row.timezone(), tenantDefaultTimezone);
        if (timezone == null) {
            return invalid("INVALID_TIMEZONE", "Invalid or missing timezone");
        }

        return switch (channel) {
            case EMAIL -> validateEmail(row, timezone);
            case SMS -> validatePhone(row, timezone);
            case PUSH -> validateDeviceToken(row, timezone);
        };
    }

    private RecipientRowValidationResult validateEmail(CampaignCsvRow row, String timezone) {
        if (isBlank(row.email())) {
            return invalid("MISSING_EMAIL", "email is required");
        }
        String normalizedEmail = row.email().strip().toLowerCase(Locale.ROOT);
        if (!EMAIL_PATTERN.matcher(normalizedEmail).matches()) {
            return invalid("INVALID_EMAIL", "email format is invalid");
        }
        return valid(normalizedEmail, normalizedEmail, null, null, timezone);
    }

    private RecipientRowValidationResult validatePhone(CampaignCsvRow row, String timezone) {
        if (isBlank(row.phoneNumber())) {
            return invalid("MISSING_PHONE_NUMBER", "phone_number is required");
        }
        String digits = NON_DIGIT_PATTERN.matcher(row.phoneNumber()).replaceAll("");
        if (digits.length() < 8 || digits.length() > 15) {
            return invalid("INVALID_PHONE_NUMBER", "phone_number must contain 8 to 15 digits");
        }
        String normalizedPhone = "+" + digits;
        return valid(normalizedPhone, null, normalizedPhone, null, timezone);
    }

    private RecipientRowValidationResult validateDeviceToken(CampaignCsvRow row, String timezone) {
        if (isBlank(row.deviceToken())) {
            return invalid("MISSING_DEVICE_TOKEN", "device_token is required");
        }
        String normalizedToken = row.deviceToken().strip();
        if (normalizedToken.length() < 8) {
            return invalid("INVALID_DEVICE_TOKEN", "device_token is too short");
        }
        return valid(normalizedToken, null, null, normalizedToken, timezone);
    }

    private RecipientRowValidationResult valid(
            String normalizedRecipientKey,
            String email,
            String phoneNumber,
            String deviceToken,
            String timezone) {
        return new RecipientRowValidationResult(
                true,
                normalizedRecipientKey,
                email,
                phoneNumber,
                deviceToken,
                timezone,
                null,
                null);
    }

    private RecipientRowValidationResult invalid(String errorCode, String errorMessage) {
        return new RecipientRowValidationResult(
                false,
                null,
                null,
                null,
                null,
                null,
                errorCode,
                errorMessage);
    }

    private String resolveTimezone(String rowTimezone, String tenantDefaultTimezone) {
        String candidate = !isBlank(rowTimezone) ? rowTimezone.strip() : null;
        if (candidate == null && !isBlank(tenantDefaultTimezone)) {
            candidate = tenantDefaultTimezone.strip();
        }
        if (candidate == null) {
            return null;
        }

        try {
            return ZoneId.of(candidate).getId();
        } catch (Exception ignored) {
            return null;
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
