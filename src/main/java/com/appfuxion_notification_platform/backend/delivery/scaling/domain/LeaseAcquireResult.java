package com.appfuxion_notification_platform.backend.delivery.scaling.domain;

public record LeaseAcquireResult(
        boolean acquired,
        PartitionLease lease) {
}
