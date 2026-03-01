package com.appfuxion_notification_platform.backend.delivery.rules;

import com.appfuxion_notification_platform.backend.delivery.persistence.NotificationJob;

public interface NotificationRuleContextLoader {

    NotificationRuleContext loadFor(NotificationJob job);
}
