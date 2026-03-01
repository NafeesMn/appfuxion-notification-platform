package com.appfuxion_notification_platform.backend.delivery.scaling;

import java.util.Set;

import com.appfuxion_notification_platform.backend.delivery.scaling.domain.WorkerIdentity;

public interface PartitionPlanner {

    Set<Integer> desiredPartitionsForWorker(
            WorkerIdentity worker,
            int totalPartitions,
            int activeWorkerCount);
}
