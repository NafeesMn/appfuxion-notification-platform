package com.appfuxion_notification_platform.backend.delivery.rules.runtime;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

import com.appfuxion_notification_platform.backend.delivery.rules.NotificationRule;
import com.appfuxion_notification_platform.backend.delivery.rules.NotificationRuleAction;
import com.appfuxion_notification_platform.backend.delivery.rules.NotificationRuleContext;
import com.appfuxion_notification_platform.backend.delivery.rules.NotificationRuleDecision;
import com.appfuxion_notification_platform.backend.delivery.rules.NotificationRuleEngine;

public class OrderedNotificationRuleEngine implements NotificationRuleEngine {

    private final List<NotificationRule> rules;

    public OrderedNotificationRuleEngine(List<NotificationRule> rules) {
        Objects.requireNonNull(rules, "rules");
        if (rules.isEmpty()) {
            throw new IllegalArgumentException("rules must not be empty");
        }
        this.rules = List.copyOf(rules);
    }

    @Override
    public NotificationRuleDecision evaluate(NotificationRuleContext context, Instant now) {
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(now, "now");

        for (NotificationRule rule : rules) {
            NotificationRuleDecision decision = Objects.requireNonNull(
                    rule.evaluate(context, now),
                    "rule decision must not be null");
            if (decision.action() != NotificationRuleAction.ALLOW) {
                return decision;
            }
        }

        return NotificationRuleDecision.allow();
    }
}
