package com.appfuxion_notification_platform.backend.delivery.scaling.runtime;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;

import org.junit.jupiter.api.Test;

import com.appfuxion_notification_platform.backend.domain.shared.NotificationChannel;

class TokenBucketChannelRateLimiterTest {

    @Test
    void beforeDispatch_shouldThrottleWhenBudgetExhaustedAndRecoverAfterRefill() {
        TokenBucketChannelRateLimiter limiter = new TokenBucketChannelRateLimiter(2);
        Instant t0 = Instant.parse("2026-03-01T00:00:00Z");

        assertTrue(limiter.beforeDispatch(NotificationChannel.SMS, t0).allowedNow());
        assertTrue(limiter.beforeDispatch(NotificationChannel.SMS, t0).allowedNow());
        assertFalse(limiter.beforeDispatch(NotificationChannel.SMS, t0).allowedNow());

        Instant afterThirtySeconds = t0.plusSeconds(30);
        assertTrue(limiter.beforeDispatch(NotificationChannel.SMS, afterThirtySeconds).allowedNow());
    }
}
