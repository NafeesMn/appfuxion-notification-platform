package com.appfuxion_notification_platform.backend.delivery.scaling.runtime;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import com.appfuxion_notification_platform.backend.delivery.scaling.GlobalChannelRateLimiter;
import com.appfuxion_notification_platform.backend.delivery.scaling.domain.BackPressureDecision;
import com.appfuxion_notification_platform.backend.domain.shared.NotificationChannel;

/**
 * Implementation: in-memory fixed-window limiter (per JVM).
 * Note: not globally shared across multiple workers/processes.
 */
public class TokenBucketChannelRateLimiter implements GlobalChannelRateLimiter {

    private final int requestsPerMinutePerChannel;
    private final Map<NotificationChannel, WindowState> windows = new ConcurrentHashMap<>();

    public TokenBucketChannelRateLimiter(int requestsPerMinutePerChannel) {
        if (requestsPerMinutePerChannel <= 0) {
            throw new IllegalArgumentException("requestsPerMinutePerChannel must be > 0");
        }
        this.requestsPerMinutePerChannel = requestsPerMinutePerChannel;
    }

    @Override
    public BackPressureDecision beforeDispatch(NotificationChannel channel, Instant now) {
        Objects.requireNonNull(channel, "channel");
        Objects.requireNonNull(now, "now");

        long epochMinute = now.getEpochSecond() / 60;
        WindowState state = windows.computeIfAbsent(channel, c -> new WindowState(epochMinute, 0));

        synchronized (state) {
            if(state.epochMinute != epochMinute) {
                state.epochMinute = epochMinute;
                state.used = 0;
            }

            if(state.used < requestsPerMinutePerChannel) {
                state.used++;
                return new BackPressureDecision(true, now, "ALLOWED");
            }

            Instant nextEligibleAt = Instant.ofEpochSecond((epochMinute + 1) * 60);
            return new BackPressureDecision(false, nextEligibleAt, "CHANNEL_RATE_LIMIT_EXCEEDED");
        }
    }

    private static final class WindowState {
        private long epochMinute;
        private int used;

        private WindowState(long epochMinute, int used) {
            this.epochMinute = epochMinute;
            this.used = used;
        }
    }
}
