package com.appfuxion_notification_platform.backend.delivery.scaling;

import com.appfuxion_notification_platform.backend.domain.shared.NotificationChannel;

public interface ScalingMetricsRecorder {

    void recordOwnedPartitions(String workerId, int count);

    void recordLeaseAcquireFailure(int partitionId);

    void recordThrottleDeferral(NotificationChannel channel);

    void recordJobLagSeconds(long seconds);
}
