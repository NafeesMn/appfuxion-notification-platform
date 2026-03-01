package com.appfuxion_notification_platform.backend.delivery.scaling.runtime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import com.appfuxion_notification_platform.backend.delivery.persistence.NotificationJob;
import com.appfuxion_notification_platform.backend.delivery.scaling.NotificationJobExecutor;
import com.appfuxion_notification_platform.backend.delivery.scaling.PartitionAwareJobFetcher;
import com.appfuxion_notification_platform.backend.delivery.scaling.PartitionLeaseService;
import com.appfuxion_notification_platform.backend.delivery.scaling.PartitionPlanner;
import com.appfuxion_notification_platform.backend.delivery.scaling.ScalingMetricsRecorder;
import com.appfuxion_notification_platform.backend.delivery.scaling.domain.LeaseAcquireResult;
import com.appfuxion_notification_platform.backend.delivery.scaling.domain.WorkerIdentity;

class DefaultPartitionedWorkerCoordinatorTest {

    @Test
    void rebalanceAndRunOnce_shouldAcquireReleaseAndExecuteOwnedPartitions() {
        PartitionLeaseService partitionLeaseService = Mockito.mock(PartitionLeaseService.class);
        PartitionPlanner partitionPlanner = Mockito.mock(PartitionPlanner.class);
        PartitionAwareJobFetcher jobFetcher = Mockito.mock(PartitionAwareJobFetcher.class);
        NotificationJobExecutor jobExecutor = Mockito.mock(NotificationJobExecutor.class);
        ScalingMetricsRecorder metricsRecorder = Mockito.mock(ScalingMetricsRecorder.class);
        WorkerIdentity worker = new WorkerIdentity("worker-a");
        Instant now = Instant.parse("2026-03-01T00:00:00Z");

        DefaultPartitionedWorkerCoordinator coordinator = new DefaultPartitionedWorkerCoordinator(
                partitionLeaseService,
                partitionPlanner,
                jobFetcher,
                jobExecutor,
                metricsRecorder,
                16,
                4,
                10,
                Duration.ofSeconds(30));

        when(partitionPlanner.desiredPartitionsForWorker(worker, 16, 4)).thenReturn(Set.of(1, 3));
        when(partitionLeaseService.tryAcquire(eq(1), eq(worker), any())).thenReturn(new LeaseAcquireResult(true, null));
        when(partitionLeaseService.tryAcquire(eq(3), eq(worker), any())).thenReturn(new LeaseAcquireResult(true, null));
        when(partitionLeaseService.listOwnedPartitions(worker)).thenReturn(Set.of(1, 3, 7), Set.of(1, 3));

        NotificationJob first = new NotificationJob();
        NotificationJob second = new NotificationJob();
        when(jobFetcher.fetchDueJobs(Set.of(1, 3), now, 10)).thenReturn(List.of(first, second));

        coordinator.rebalanceAndRunOnce(worker, now);

        verify(partitionLeaseService).release(7, worker);
        verify(jobExecutor).execute(first, worker, now);
        verify(jobExecutor).execute(second, worker, now);
        verify(metricsRecorder).recordOwnedPartitions("worker-a", 2);
        verify(partitionLeaseService, atLeast(1)).heartbeat(1, worker, Duration.ofSeconds(30));
        verify(partitionLeaseService, atLeast(1)).heartbeat(3, worker, Duration.ofSeconds(30));
    }

    @Test
    void rebalanceAndRunOnce_shouldSkipFetchWhenNoOwnedPartitions() {
        PartitionLeaseService partitionLeaseService = Mockito.mock(PartitionLeaseService.class);
        PartitionPlanner partitionPlanner = Mockito.mock(PartitionPlanner.class);
        PartitionAwareJobFetcher jobFetcher = Mockito.mock(PartitionAwareJobFetcher.class);
        NotificationJobExecutor jobExecutor = Mockito.mock(NotificationJobExecutor.class);
        ScalingMetricsRecorder metricsRecorder = Mockito.mock(ScalingMetricsRecorder.class);
        WorkerIdentity worker = new WorkerIdentity("worker-a");
        Instant now = Instant.parse("2026-03-01T00:00:00Z");

        DefaultPartitionedWorkerCoordinator coordinator = new DefaultPartitionedWorkerCoordinator(
                partitionLeaseService,
                partitionPlanner,
                jobFetcher,
                jobExecutor,
                metricsRecorder,
                16,
                4,
                10,
                Duration.ofSeconds(30));

        when(partitionPlanner.desiredPartitionsForWorker(worker, 16, 4)).thenReturn(Set.of(1));
        when(partitionLeaseService.tryAcquire(eq(1), eq(worker), any())).thenReturn(new LeaseAcquireResult(false, null));
        when(partitionLeaseService.listOwnedPartitions(worker)).thenReturn(Set.of(), Set.of());

        coordinator.rebalanceAndRunOnce(worker, now);

        verify(metricsRecorder).recordLeaseAcquireFailure(1);
        verify(metricsRecorder).recordOwnedPartitions("worker-a", 0);
        verify(jobFetcher, never()).fetchDueJobs(any(), any(), anyInt());
        verify(jobExecutor, never()).execute(any(), any(), any());
    }
}
