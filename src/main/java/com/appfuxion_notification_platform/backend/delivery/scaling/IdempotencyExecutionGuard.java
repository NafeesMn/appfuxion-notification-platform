package com.appfuxion_notification_platform.backend.delivery.scaling;

import com.appfuxion_notification_platform.backend.delivery.persistence.NotificationJob;

public interface IdempotencyExecutionGuard {

    boolean alreadyDelivered(NotificationJob job);
}
