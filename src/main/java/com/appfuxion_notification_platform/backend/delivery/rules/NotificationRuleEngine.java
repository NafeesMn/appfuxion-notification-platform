package com.appfuxion_notification_platform.backend.delivery.rules;

import java.time.Instant;

public interface NotificationRuleEngine {

    NotificationRuleDecision evaluate(NotificationRuleContext context, Instant now);
}
