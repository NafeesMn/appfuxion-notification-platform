package com.appfuxion_notification_platform.backend.delivery.rules;

import java.time.Instant;
import java.util.Objects;

public record NotificationRuleDecision(
        NotificationRuleAction action,
        String reasonCode,
        Instant nextEligibleAt) {

    public NotificationRuleDecision {
        Objects.requireNonNull(action, "action");
        if (action == NotificationRuleAction.DELAY) {
            Objects.requireNonNull(nextEligibleAt, "nextEligibleAt");
        }
    }

    public static NotificationRuleDecision allow() {
        return new NotificationRuleDecision(NotificationRuleAction.ALLOW, "ALLOWED", null);
    }

    public static NotificationRuleDecision skip(String reasonCode) {
        return new NotificationRuleDecision(NotificationRuleAction.SKIP, nonBlankReason(reasonCode), null);
    }

    public static NotificationRuleDecision delay(String reasonCode, Instant nextEligibleAt) {
        return new NotificationRuleDecision(
                NotificationRuleAction.DELAY,
                nonBlankReason(reasonCode),
                Objects.requireNonNull(nextEligibleAt, "nextEligibleAt"));
    }

    private static String nonBlankReason(String reasonCode) {
        if (reasonCode == null || reasonCode.isBlank()) {
            throw new IllegalArgumentException("reasonCode must not be blank");
        }
        return reasonCode;
    }
}
