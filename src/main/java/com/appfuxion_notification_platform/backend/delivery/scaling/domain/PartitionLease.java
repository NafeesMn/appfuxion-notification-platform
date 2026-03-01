package com.appfuxion_notification_platform.backend.delivery.scaling.domain;

import java.time.Instant;

public record PartitionLease(
        int partitionId,
        String workerId,
        Instant leaseExpiresAt,
        Instant heartbeatAt,
        long version) {
}
