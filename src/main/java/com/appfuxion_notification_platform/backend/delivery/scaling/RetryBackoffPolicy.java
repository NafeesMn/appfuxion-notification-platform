package com.appfuxion_notification_platform.backend.delivery.scaling;

import java.time.Instant;

public interface RetryBackoffPolicy {

    Instant nextAttemptAt(int attemptCountAfterFailure, Instant now);
}
