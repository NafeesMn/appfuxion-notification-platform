package com.appfuxion_notification_platform.backend.delivery.rules.runtime;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Objects;

import com.appfuxion_notification_platform.backend.domain.shared.CampaignType;
import com.appfuxion_notification_platform.backend.domain.shared.NotificationChannel;
import com.appfuxion_notification_platform.backend.delivery.rules.NotificationRule;
import com.appfuxion_notification_platform.backend.delivery.rules.NotificationRuleContext;
import com.appfuxion_notification_platform.backend.delivery.rules.NotificationRuleDecision;

public class QuietHoursRule implements NotificationRule {

    private static final String REASON = "QUIET_HOURS_ACTIVE";
    private static final LocalTime QUIET_START = LocalTime.of(22, 0);
    private static final LocalTime QUIET_END = LocalTime.of(8, 0);

    @Override
    public NotificationRuleDecision evaluate(NotificationRuleContext context, Instant now) {
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(now, "now");

        NotificationChannel channel = context.job().getChannel();
        if (channel != NotificationChannel.SMS && channel != NotificationChannel.PUSH) {
            return NotificationRuleDecision.allow();
        }

        if (context.campaign().getCampaignType() == CampaignType.TRANSACTIONAL) {
            return NotificationRuleDecision.allow();
        }

        ZoneId zoneId = resolveZone(context.recipient().getTimezone(), context.tenant().getDefaultTimezone());
        ZonedDateTime recipientNow = now.atZone(zoneId);
        LocalTime localTime = recipientNow.toLocalTime();

        if (!isQuietHours(localTime)) {
            return NotificationRuleDecision.allow();
        }

        Instant nextEligibleAt = nextAllowedInstant(recipientNow);
        return NotificationRuleDecision.delay(REASON, nextEligibleAt);
    }

    private boolean isQuietHours(LocalTime localTime) {
        return !localTime.isBefore(QUIET_START) || localTime.isBefore(QUIET_END);
    }

    private Instant nextAllowedInstant(ZonedDateTime recipientNow) {
        LocalDate date = recipientNow.toLocalDate();
        LocalTime localTime = recipientNow.toLocalTime();

        LocalDate eligibleDate = localTime.isBefore(QUIET_END) ? date : date.plusDays(1);
        return ZonedDateTime.of(eligibleDate, QUIET_END, recipientNow.getZone()).toInstant();
    }

    private ZoneId resolveZone(String recipientTimezone, String tenantDefaultTimezone) {
        String timezone = firstNonBlank(recipientTimezone, tenantDefaultTimezone, "UTC");
        try {
            return ZoneId.of(timezone);
        } catch (Exception ex) {
            if (tenantDefaultTimezone != null && !tenantDefaultTimezone.isBlank() && !tenantDefaultTimezone.equals(timezone)) {
                try {
                    return ZoneId.of(tenantDefaultTimezone);
                } catch (Exception ignored) {
                    // fall through to UTC
                }
            }
            return ZoneId.of("UTC");
        }
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        throw new IllegalStateException("No fallback timezone provided");
    }
}
