package com.appfuxion_notification_platform.backend.delivery.scaling.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import com.appfuxion_notification_platform.backend.delivery.scaling.domain.LeaseAcquireResult;
import com.appfuxion_notification_platform.backend.delivery.scaling.domain.WorkerIdentity;
import com.appfuxion_notification_platform.backend.operations.persistence.WorkerPartitionLease;
import com.appfuxion_notification_platform.backend.operations.persistence.WorkerPartitionLeaseRepository;

class DatabasePartitionLeaseServiceTest {

    private static final WorkerIdentity WORKER_A = new WorkerIdentity("worker-a");
    private static final WorkerIdentity WORKER_B = new WorkerIdentity("worker-b");
    private static final Duration LEASE_TTL = Duration.ofSeconds(30);
    private static final Instant NOW = Instant.parse("2026-03-01T00:00:00Z");

    @Test
    void tryAcquire_shouldCreateLeaseWhenMissing() {
        WorkerPartitionLeaseRepository repository = Mockito.mock(WorkerPartitionLeaseRepository.class);
        DatabasePartitionLeaseService service = new DatabasePartitionLeaseService(
                repository,
                Clock.fixed(NOW, ZoneOffset.UTC));

        when(repository.findById(10)).thenReturn(Optional.empty());
        when(repository.save(any(WorkerPartitionLease.class))).thenAnswer(invocation -> invocation.getArgument(0));

        LeaseAcquireResult result = service.tryAcquire(10, WORKER_A, LEASE_TTL);

        assertTrue(result.acquired());
        assertNotNull(result.lease());
        assertEquals(10, result.lease().partitionId());
        assertEquals("worker-a", result.lease().workerId());
        assertEquals(NOW.plus(LEASE_TTL), result.lease().leaseExpiresAt());
    }

    @Test
    void tryAcquire_shouldRejectWhenOwnedByAnotherWorkerAndNotExpired() {
        WorkerPartitionLeaseRepository repository = Mockito.mock(WorkerPartitionLeaseRepository.class);
        DatabasePartitionLeaseService service = new DatabasePartitionLeaseService(
                repository,
                Clock.fixed(NOW, ZoneOffset.UTC));

        WorkerPartitionLease existing = lease(10, "worker-b", NOW.minusSeconds(5), NOW.plusSeconds(60));
        when(repository.findById(10)).thenReturn(Optional.of(existing));

        LeaseAcquireResult result = service.tryAcquire(10, WORKER_A, LEASE_TTL);

        assertFalse(result.acquired());
        assertNotNull(result.lease());
        assertEquals("worker-b", result.lease().workerId());
        verify(repository, never()).save(any());
    }

    @Test
    void heartbeat_shouldReturnFalseWhenLeaseOwnedByAnotherWorker() {
        WorkerPartitionLeaseRepository repository = Mockito.mock(WorkerPartitionLeaseRepository.class);
        DatabasePartitionLeaseService service = new DatabasePartitionLeaseService(
                repository,
                Clock.fixed(NOW, ZoneOffset.UTC));

        when(repository.findById(10)).thenReturn(Optional.of(lease(10, "worker-b", NOW, NOW.plusSeconds(10))));

        boolean renewed = service.heartbeat(10, WORKER_A, LEASE_TTL);

        assertFalse(renewed);
        verify(repository, never()).save(any());
    }

    @Test
    void release_shouldDeleteWhenOwnedByCaller() {
        WorkerPartitionLeaseRepository repository = Mockito.mock(WorkerPartitionLeaseRepository.class);
        DatabasePartitionLeaseService service = new DatabasePartitionLeaseService(
                repository,
                Clock.fixed(NOW, ZoneOffset.UTC));

        WorkerPartitionLease lease = lease(10, "worker-a", NOW, NOW.plusSeconds(10));
        when(repository.findById(10)).thenReturn(Optional.of(lease));

        service.release(10, WORKER_A);

        verify(repository).delete(lease);
    }

    @Test
    void listOwnedPartitions_shouldReturnOnlyUnexpiredPartitionsForWorker() {
        WorkerPartitionLeaseRepository repository = Mockito.mock(WorkerPartitionLeaseRepository.class);
        DatabasePartitionLeaseService service = new DatabasePartitionLeaseService(
                repository,
                Clock.fixed(NOW, ZoneOffset.UTC));

        when(repository.findByWorkerIdAndLeaseExpiresAtAfter("worker-a", NOW))
                .thenReturn(List.of(
                        lease(1, "worker-a", NOW.minusSeconds(1), NOW.plusSeconds(10)),
                        lease(3, "worker-a", NOW.minusSeconds(1), NOW.plusSeconds(20))));

        Set<Integer> owned = service.listOwnedPartitions(WORKER_A);

        assertEquals(Set.of(1, 3), owned);
    }

    @Test
    void reclaimExpiredLeases_shouldDeleteByCutoff() {
        WorkerPartitionLeaseRepository repository = Mockito.mock(WorkerPartitionLeaseRepository.class);
        DatabasePartitionLeaseService service = new DatabasePartitionLeaseService(
                repository,
                Clock.fixed(NOW, ZoneOffset.UTC));

        when(repository.deleteByLeaseExpiresAtBefore(eq(NOW))).thenReturn(4L);

        int reclaimed = service.reclaimExpiredLeases(NOW);

        assertEquals(4, reclaimed);
        ArgumentCaptor<Instant> cutoff = ArgumentCaptor.forClass(Instant.class);
        verify(repository).deleteByLeaseExpiresAtBefore(cutoff.capture());
        assertEquals(NOW, cutoff.getValue());
    }

    private static WorkerPartitionLease lease(int partitionId, String workerId, Instant heartbeatAt, Instant leaseExpiresAt) {
        WorkerPartitionLease lease = new WorkerPartitionLease();
        lease.setPartitionId(partitionId);
        lease.setWorkerId(workerId);
        lease.setHeartbeatAt(heartbeatAt);
        lease.setLeaseExpiresAt(leaseExpiresAt);
        return lease;
    }
}
