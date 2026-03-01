package com.appfuxion_notification_platform.backend.delivery.rules;

import java.time.Instant;

public interface NotificationRule {

    NotificationRuleDecision evaluate(NotificationRuleContext context, Instant now);
}
