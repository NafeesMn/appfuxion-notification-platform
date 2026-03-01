package com.appfuxion_notification_platform.backend.delivery.scaling;

import java.time.Duration;
import java.time.Instant;
import java.util.Set;

import com.appfuxion_notification_platform.backend.delivery.scaling.domain.LeaseAcquireResult;
import com.appfuxion_notification_platform.backend.delivery.scaling.domain.WorkerIdentity;

public interface PartitionLeaseService {

    LeaseAcquireResult tryAcquire(int partitionId, WorkerIdentity worker, Duration leaseTtl);

    boolean heartbeat(int partitionId, WorkerIdentity worker, Duration leaseTtl);

    void release(int partitionId, WorkerIdentity worker);

    Set<Integer> listOwnedPartitions(WorkerIdentity worker);

    int reclaimExpiredLeases(Instant now);
}
