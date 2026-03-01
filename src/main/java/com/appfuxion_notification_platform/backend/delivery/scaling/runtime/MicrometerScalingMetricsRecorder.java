package com.appfuxion_notification_platform.backend.delivery.scaling.runtime;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import com.appfuxion_notification_platform.backend.delivery.scaling.ScalingMetricsRecorder;
import com.appfuxion_notification_platform.backend.domain.shared.NotificationChannel;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;

@Component
@Primary
public class MicrometerScalingMetricsRecorder implements ScalingMetricsRecorder {

    private static final String PREFIX = "notification_platform.scaling";

    private final MeterRegistry meterRegistry;
    private final ConcurrentMap<String, AtomicInteger> ownedPartitionsGaugeByWorker = new ConcurrentHashMap<>();
    private final ConcurrentMap<Integer, Counter> leaseAcquireFailureCounterByPartition = new ConcurrentHashMap<>();
    private final ConcurrentMap<NotificationChannel, Counter> throttleDeferralCounterByChannel = new ConcurrentHashMap<>();
    private final DistributionSummary jobLagSummary;

    public MicrometerScalingMetricsRecorder(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        this.jobLagSummary = DistributionSummary.builder(PREFIX + ".job.lag.seconds")
                .description("Lag between scheduled next_attempt_at and actual execution time")
                .baseUnit("seconds")
                .register(meterRegistry);
    }

    @Override
    public void recordOwnedPartitions(String workerId, int count) {
        if (workerId == null || workerId.isBlank()) {
            throw new IllegalArgumentException("workerId must not be blank");
        }
        if (count < 0) {
            throw new IllegalArgumentException("count must be >= 0");
        }

        AtomicInteger gauge = ownedPartitionsGaugeByWorker.computeIfAbsent(workerId, key -> {
            AtomicInteger atomic = new AtomicInteger(0);
            Gauge.builder(PREFIX + ".owned.partitions", atomic, AtomicInteger::get)
                    .description("Number of partitions currently owned by worker")
                    .tag("workerId", key)
                    .register(meterRegistry);
            return atomic;
        });

        gauge.set(count);
    }

    @Override
    public void recordLeaseAcquireFailure(int partitionId) {
        if (partitionId < 0) {
            throw new IllegalArgumentException("partitionId must be >= 0");
        }

        Counter counter = leaseAcquireFailureCounterByPartition.computeIfAbsent(partitionId, key ->
                Counter.builder(PREFIX + ".lease.acquire.failures")
                        .description("Failed partition lease acquisitions")
                        .tag("partitionId", String.valueOf(key))
                        .register(meterRegistry));

        counter.increment();
    }

    @Override
    public void recordThrottleDeferral(NotificationChannel channel) {
        if (channel == null) {
            throw new IllegalArgumentException("channel must not be null");
        }

        Counter counter = throttleDeferralCounterByChannel.computeIfAbsent(channel, key ->
                Counter.builder(PREFIX + ".throttle.deferrals")
                        .description("Jobs deferred due to channel-level throttling")
                        .tag("channel", key.name())
                        .register(meterRegistry));

        counter.increment();
    }

    @Override
    public void recordJobLagSeconds(long seconds) {
        if (seconds < 0) {
            throw new IllegalArgumentException("seconds must be >= 0");
        }
        jobLagSummary.record(seconds);
    }
}
