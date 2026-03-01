package com.appfuxion_notification_platform.backend.delivery.scaling;

import java.time.Instant;

import com.appfuxion_notification_platform.backend.delivery.scaling.domain.BackPressureDecision;
import com.appfuxion_notification_platform.backend.domain.shared.NotificationChannel;

public interface GlobalChannelRateLimiter {

    BackPressureDecision beforeDispatch(NotificationChannel channel, Instant now);
}
