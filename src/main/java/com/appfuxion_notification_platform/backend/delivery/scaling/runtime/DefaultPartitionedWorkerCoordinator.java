package com.appfuxion_notification_platform.backend.delivery.scaling.runtime;

import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import com.appfuxion_notification_platform.backend.delivery.persistence.NotificationJob;
import com.appfuxion_notification_platform.backend.delivery.scaling.NotificationJobExecutor;
import com.appfuxion_notification_platform.backend.delivery.scaling.PartitionAwareJobFetcher;
import com.appfuxion_notification_platform.backend.delivery.scaling.PartitionLeaseService;
import com.appfuxion_notification_platform.backend.delivery.scaling.PartitionPlanner;
import com.appfuxion_notification_platform.backend.delivery.scaling.PartitionedWorkerCoordinator;
import com.appfuxion_notification_platform.backend.delivery.scaling.ScalingMetricsRecorder;
import com.appfuxion_notification_platform.backend.delivery.scaling.domain.WorkerIdentity;

public class DefaultPartitionedWorkerCoordinator implements PartitionedWorkerCoordinator {

    private final PartitionLeaseService partitionLeaseService;
    private final PartitionPlanner partitionPlanner;
    private final PartitionAwareJobFetcher jobFetcher;
    private final NotificationJobExecutor jobExecutor;
    private final ScalingMetricsRecorder metricsRecorder;
    private final int totalPartitions;
    private final int activeWorkerCount;
    private final int pollBatchSize;
    private final Duration leaseTtl;

    public DefaultPartitionedWorkerCoordinator(
            PartitionLeaseService partitionLeaseService,
            PartitionPlanner partitionPlanner,
            PartitionAwareJobFetcher jobFetcher,
            NotificationJobExecutor jobExecutor,
            ScalingMetricsRecorder metricsRecorder,
            int totalPartitions,
            int activeWorkerCount,
            int pollBatchSize,
            Duration leaseTtl) {
        this.partitionLeaseService = Objects.requireNonNull(partitionLeaseService);
        this.partitionPlanner = Objects.requireNonNull(partitionPlanner);
        this.jobFetcher = Objects.requireNonNull(jobFetcher);
        this.jobExecutor = Objects.requireNonNull(jobExecutor);
        this.metricsRecorder = Objects.requireNonNull(metricsRecorder);
        if (totalPartitions <= 0) {
            throw new IllegalArgumentException("totalPartitions must be > 0");
        }
        if (activeWorkerCount <= 0) {
            throw new IllegalArgumentException("activeWorkerCount must be > 0");
        }
        if (pollBatchSize <= 0) {
            throw new IllegalArgumentException("pollBatchSize must be > 0");
        }
        if (leaseTtl.isZero() || leaseTtl.isNegative()) {
            throw new IllegalArgumentException("leaseTtl must be > 0");
        }
        this.totalPartitions = totalPartitions;
        this.activeWorkerCount = activeWorkerCount;
        this.pollBatchSize = pollBatchSize;
        this.leaseTtl = Objects.requireNonNull(leaseTtl);
    }

    @Override
    public void rebalanceAndRunOnce(WorkerIdentity worker, Instant now) {
        Objects.requireNonNull(worker, "worker");
        Objects.requireNonNull(now, "now");

        // Phase 4 step 1: compute desired partitions for this worker.
        Set<Integer> desiredPartitions = partitionPlanner.desiredPartitionsForWorker(
                worker,
                totalPartitions,
                activeWorkerCount);

        // Phase 4 step 2: acquire/renew leases for desired partitions with leaseTtl.
        for (Integer desiredPartition : desiredPartitions) {
            var acquireResult = partitionLeaseService.tryAcquire(desiredPartition, worker, leaseTtl);
            if (!acquireResult.acquired()) {
                metricsRecorder.recordLeaseAcquireFailure(desiredPartition);
            }
        }

        // Phase 4 step 3: release leases no longer desired.
        Set<Integer> currentlyOwned = partitionLeaseService.listOwnedPartitions(worker);
        Set<Integer> partitionsToRelease = new HashSet<>(currentlyOwned);
        partitionsToRelease.removeAll(desiredPartitions);
        for (Integer partitionToRelease : partitionsToRelease) {
            partitionLeaseService.release(partitionToRelease, worker);
        }

        // Phase 4 step 4: fetch due jobs only for owned partitions, bounded by pollBatchSize.
        Set<Integer> ownedPartitions = partitionLeaseService.listOwnedPartitions(worker);
        metricsRecorder.recordOwnedPartitions(worker.workerId(), ownedPartitions.size());
        if (ownedPartitions.isEmpty()) {
            return;
        }

        List<NotificationJob> dueJobs = jobFetcher.fetchDueJobs(ownedPartitions, now, pollBatchSize);

        // Phase 4 step 5: execute jobs with back-pressure and idempotency-safe semantics.
        // Phase 4 step 6: heartbeat leases while long batches are in-flight.
        int heartbeatEvery = Math.max(1, Math.min(50, dueJobs.size()));
        int processed = 0;
        for (NotificationJob dueJob : dueJobs) {
            jobExecutor.execute(dueJob, worker, now);
            processed++;
            if (processed % heartbeatEvery == 0) {
                heartbeatOwnedPartitions(ownedPartitions, worker);
            }
        }

        heartbeatOwnedPartitions(ownedPartitions, worker);
    }

    private void heartbeatOwnedPartitions(Set<Integer> ownedPartitions, WorkerIdentity worker) {
        for (Integer ownedPartition : ownedPartitions) {
            partitionLeaseService.heartbeat(ownedPartition, worker, leaseTtl);
        }
    }
}
