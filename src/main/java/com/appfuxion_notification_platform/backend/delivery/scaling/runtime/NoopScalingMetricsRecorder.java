package com.appfuxion_notification_platform.backend.delivery.scaling.runtime;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

import com.appfuxion_notification_platform.backend.delivery.scaling.ScalingMetricsRecorder;
import com.appfuxion_notification_platform.backend.domain.shared.NotificationChannel;

/**
 * Phase 4 local metrics recorder.
 *
 * This implementation stores metrics in memory for development/testing only.
 * Replace with Micrometer-backed instrumentation in Phase 8.
 */
public class NoopScalingMetricsRecorder implements ScalingMetricsRecorder {

    private final ConcurrentMap<String, Integer> ownedPartitionsByWorker = new ConcurrentHashMap<>();
    private final LongAdder leaseAcquireFailureCount = new LongAdder();
    private final ConcurrentMap<NotificationChannel, LongAdder> throttleDeferralsByChannel = new ConcurrentHashMap<>();
    private final LongAdder jobLagSampleCount = new LongAdder();
    private final LongAdder totalJobLagSeconds = new LongAdder();
    private final AtomicLong maxJobLagSeconds = new AtomicLong(0L);

    @Override
    public void recordOwnedPartitions(String workerId, int count) {
        if (workerId == null || workerId.isBlank()) {
            throw new IllegalArgumentException("workerId must not be blank");
        }
        if (count < 0) {
            throw new IllegalArgumentException("count must be >= 0");
        }

        ownedPartitionsByWorker.put(workerId, count);
    }

    @Override
    public void recordLeaseAcquireFailure(int partitionId) {
        if (partitionId < 0) {
            throw new IllegalArgumentException("partitionId must be >= 0");
        }

        leaseAcquireFailureCount.increment();
    }

    @Override
    public void recordThrottleDeferral(NotificationChannel channel) {
        if (channel == null) {
            throw new IllegalArgumentException("channel must not be null");
        }

        throttleDeferralsByChannel
                .computeIfAbsent(channel, ignored -> new LongAdder())
                .increment();
    }

    @Override
    public void recordJobLagSeconds(long seconds) {
        if (seconds < 0) {
            throw new IllegalArgumentException("seconds must be >= 0");
        }

        jobLagSampleCount.increment();
        totalJobLagSeconds.add(seconds);
        maxJobLagSeconds.accumulateAndGet(seconds, Math::max);
    }

    // Snapshot helpers for tests and local diagnostics.
    public Map<String, Integer> ownedPartitionsSnapshot() {
        return Map.copyOf(ownedPartitionsByWorker);
    }

    public long leaseAcquireFailureCount() {
        return leaseAcquireFailureCount.sum();
    }

    public Map<NotificationChannel, Long> throttleDeferralsSnapshot() {
        Map<NotificationChannel, Long> snapshot = new ConcurrentHashMap<>();
        throttleDeferralsByChannel.forEach((channel, adder) -> snapshot.put(channel, adder.sum()));
        return Map.copyOf(snapshot);
    }

    public long jobLagSampleCount() {
        return jobLagSampleCount.sum();
    }

    public long totalJobLagSeconds() {
        return totalJobLagSeconds.sum();
    }

    public long maxJobLagSeconds() {
        return maxJobLagSeconds.get();
    }

    public double averageJobLagSeconds() {
        long samples = jobLagSampleCount.sum();
        if (samples == 0) {
            return 0.0d;
        }
        return (double) totalJobLagSeconds.sum() / samples;
    }
}