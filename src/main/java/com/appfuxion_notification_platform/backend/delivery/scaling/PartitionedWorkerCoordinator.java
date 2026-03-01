package com.appfuxion_notification_platform.backend.delivery.scaling;

import java.time.Instant;

import com.appfuxion_notification_platform.backend.delivery.scaling.domain.WorkerIdentity;

public interface PartitionedWorkerCoordinator {

    void rebalanceAndRunOnce(WorkerIdentity worker, Instant now);
}
