package com.appfuxion_notification_platform.backend.delivery.scaling.runtime;

import java.time.Clock;
import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.appfuxion_notification_platform.backend.delivery.scaling.PartitionLeaseService;
import com.appfuxion_notification_platform.backend.delivery.scaling.PartitionedWorkerCoordinator;
import com.appfuxion_notification_platform.backend.delivery.scaling.domain.WorkerIdentity;

import jakarta.annotation.PreDestroy;

@Component
@ConditionalOnProperty(name = "app.scaling.worker.enabled", havingValue = "true")
public class PartitionedWorkerLoop {

    private static final Logger log = LoggerFactory.getLogger(PartitionedWorkerLoop.class);

    private final PartitionedWorkerCoordinator partitionedWorkerCoordinator;
    private final PartitionLeaseService partitionLeaseService;
    private final WorkerIdentity workerIdentity;
    private final Clock clock;

    public PartitionedWorkerLoop(
            PartitionedWorkerCoordinator partitionedWorkerCoordinator,
            PartitionLeaseService partitionLeaseService,
            WorkerIdentity workerIdentity,
            Clock clock) {
        this.partitionedWorkerCoordinator = partitionedWorkerCoordinator;
        this.partitionLeaseService = partitionLeaseService;
        this.workerIdentity = workerIdentity;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${app.scaling.poll-delay-ms:500}")
    public void poll() {
        Instant now = Instant.now(clock);
        try {
            int reclaimed = partitionLeaseService.reclaimExpiredLeases(now);
            if (reclaimed > 0) {
                log.debug("worker.reclaim_expired_leases workerId={} reclaimed={}", workerIdentity.workerId(), reclaimed);
            }
            partitionedWorkerCoordinator.rebalanceAndRunOnce(workerIdentity, now);
        } catch (Exception ex) {
            log.error("worker.loop_failed workerId={}", workerIdentity.workerId(), ex);
        }
    }

    @PreDestroy
    public void releaseOwnedPartitions() {
        try {
            for (Integer partitionId : partitionLeaseService.listOwnedPartitions(workerIdentity)) {
                partitionLeaseService.release(partitionId, workerIdentity);
            }
        } catch (Exception ex) {
            log.warn("worker.release_on_shutdown_failed workerId={}", workerIdentity.workerId(), ex);
        }
    }
}
