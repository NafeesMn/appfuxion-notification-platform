package com.appfuxion_notification_platform.backend.delivery.scaling.runtime;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import com.appfuxion_notification_platform.backend.delivery.scaling.GlobalChannelRateLimiter;
import com.appfuxion_notification_platform.backend.delivery.scaling.domain.BackPressureDecision;
import com.appfuxion_notification_platform.backend.domain.shared.NotificationChannel;

/**
 * Implementation: in-memory token bucket limiter (per JVM).
 * Note: not globally shared across multiple workers/processes.
 */
public class TokenBucketChannelRateLimiter implements GlobalChannelRateLimiter {

    private static final double MILLIS_PER_MINUTE = 60_000.0d;

    private final int requestsPerMinutePerChannel;
    private final double refillTokensPerMillis;
    private final Map<NotificationChannel, TokenBucketState> buckets = new ConcurrentHashMap<>();

    public TokenBucketChannelRateLimiter(int requestsPerMinutePerChannel) {
        if (requestsPerMinutePerChannel <= 0) {
            throw new IllegalArgumentException("requestsPerMinutePerChannel must be > 0");
        }
        this.requestsPerMinutePerChannel = requestsPerMinutePerChannel;
        this.refillTokensPerMillis = requestsPerMinutePerChannel / MILLIS_PER_MINUTE;
    }

    @Override
    public BackPressureDecision beforeDispatch(NotificationChannel channel, Instant now) {
        Objects.requireNonNull(channel, "channel");
        Objects.requireNonNull(now, "now");

        long nowMillis = now.toEpochMilli();
        TokenBucketState state = buckets.computeIfAbsent(
                channel,
                ignored -> new TokenBucketState(requestsPerMinutePerChannel, nowMillis));

        synchronized (state) {
            long elapsedMillis = Math.max(0L, nowMillis - state.lastRefillEpochMillis);
            if (elapsedMillis > 0L) {
                double refill = elapsedMillis * refillTokensPerMillis;
                state.tokens = Math.min(requestsPerMinutePerChannel, state.tokens + refill);
                state.lastRefillEpochMillis = nowMillis;
            }

            if (state.tokens >= 1.0d) {
                state.tokens -= 1.0d;
                return new BackPressureDecision(true, now, "ALLOWED");
            }

            double missingTokens = 1.0d - state.tokens;
            long waitMillis = Math.max(1L, (long) Math.ceil(missingTokens / refillTokensPerMillis));
            Instant nextEligibleAt = now.plusMillis(waitMillis);
            return new BackPressureDecision(false, nextEligibleAt, "CHANNEL_RATE_LIMIT_EXCEEDED");
        }
    }

    private static final class TokenBucketState {
        private double tokens;
        private long lastRefillEpochMillis;

        private TokenBucketState(double initialTokens, long lastRefillEpochMillis) {
            this.tokens = initialTokens;
            this.lastRefillEpochMillis = lastRefillEpochMillis;
        }
    }
}
