package com.appfuxion_notification_platform.backend.delivery.scaling.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.Test;

class ExponentialRetryBackoffPolicyTest {

    @Test
    void nextAttemptAt_shouldApplyExponentialGrowthAndCapAtMaxDelay() {
        ExponentialRetryBackoffPolicy policy = new ExponentialRetryBackoffPolicy(
                Duration.ofSeconds(10),
                2,
                Duration.ofSeconds(60));
        Instant now = Instant.parse("2026-03-01T00:00:00Z");

        assertEquals(Instant.parse("2026-03-01T00:00:10Z"), policy.nextAttemptAt(1, now));
        assertEquals(Instant.parse("2026-03-01T00:00:20Z"), policy.nextAttemptAt(2, now));
        assertEquals(Instant.parse("2026-03-01T00:00:40Z"), policy.nextAttemptAt(3, now));
        assertEquals(Instant.parse("2026-03-01T00:01:00Z"), policy.nextAttemptAt(4, now));
        assertEquals(Instant.parse("2026-03-01T00:01:00Z"), policy.nextAttemptAt(5, now));
    }

    @Test
    void nextAttemptAt_shouldRejectNonPositiveAttemptNumber() {
        ExponentialRetryBackoffPolicy policy = new ExponentialRetryBackoffPolicy(
                Duration.ofSeconds(5),
                2,
                Duration.ofSeconds(30));

        assertThrows(IllegalArgumentException.class, () -> policy.nextAttemptAt(0, Instant.now()));
    }
}
