package com.appfuxion_notification_platform.backend.delivery.scaling.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import com.appfuxion_notification_platform.backend.domain.shared.NotificationChannel;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

class MicrometerScalingMetricsRecorderTest {

    @Test
    void shouldRecordOwnedPartitionsThrottleAndLagMetrics() {
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        MicrometerScalingMetricsRecorder recorder = new MicrometerScalingMetricsRecorder(meterRegistry);

        recorder.recordOwnedPartitions("worker-a", 3);
        recorder.recordLeaseAcquireFailure(4);
        recorder.recordThrottleDeferral(NotificationChannel.SMS);
        recorder.recordThrottleDeferral(NotificationChannel.SMS);
        recorder.recordJobLagSeconds(7);
        recorder.recordJobLagSeconds(3);

        double ownedGauge = meterRegistry.get("notification_platform.scaling.owned.partitions")
                .tag("workerId", "worker-a")
                .gauge()
                .value();
        double leaseCounter = meterRegistry.get("notification_platform.scaling.lease.acquire.failures")
                .tag("partitionId", "4")
                .counter()
                .count();
        double throttleCounter = meterRegistry.get("notification_platform.scaling.throttle.deferrals")
                .tag("channel", "SMS")
                .counter()
                .count();
        var lagSummary = meterRegistry.get("notification_platform.scaling.job.lag.seconds")
                .summary();

        assertEquals(3.0d, ownedGauge);
        assertEquals(1.0d, leaseCounter);
        assertEquals(2.0d, throttleCounter);
        assertEquals(2L, lagSummary.count());
        assertEquals(10.0d, lagSummary.totalAmount());
    }
}
