package com.appfuxion_notification_platform.backend.delivery.scaling.runtime;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

import com.appfuxion_notification_platform.backend.delivery.scaling.RetryBackoffPolicy;

public class ExponentialRetryBackoffPolicy implements RetryBackoffPolicy {

    private final Duration baseDelay;
    private final int multiplier;
    private final Duration maxDelay;

    public ExponentialRetryBackoffPolicy(Duration baseDelay, int multiplier, Duration maxDelay) {
        this.baseDelay = requirePositive(baseDelay, "baseDelay");
        if (multiplier < 1) {
            throw new IllegalArgumentException("multiplier must be >= 1");
        }
        this.multiplier = multiplier;
        this.maxDelay = requirePositive(maxDelay, "maxDelay");
        if (maxDelay.compareTo(baseDelay) < 0) {
            throw new IllegalArgumentException("maxDelay must be >= baseDelay");
        }
    }

    @Override
    public Instant nextAttemptAt(int attemptCountAfterFailure, Instant now) {
        Objects.requireNonNull(now, "now");
        if (attemptCountAfterFailure <= 0) {
            throw new IllegalArgumentException("attemptCountAfterFailure must be > 0");
        }
        return now.plus(backoffDelay(attemptCountAfterFailure));
    }

    private Duration backoffDelay(int attemptCountAfterFailure) {
        Duration delay = baseDelay;
        for (int i = 1; i < attemptCountAfterFailure; i++) {
            Duration multiplied = delay.multipliedBy(multiplier);
            if (multiplied.compareTo(maxDelay) >= 0) {
                return maxDelay;
            }
            delay = multiplied;
        }
        if (delay.compareTo(maxDelay) > 0) {
            return maxDelay;
        }
        return delay;
    }

    private Duration requirePositive(Duration duration, String fieldName) {
        Objects.requireNonNull(duration, fieldName);
        if (duration.isZero() || duration.isNegative()) {
            throw new IllegalArgumentException(fieldName + " must be > 0");
        }
        return duration;
    }
}
