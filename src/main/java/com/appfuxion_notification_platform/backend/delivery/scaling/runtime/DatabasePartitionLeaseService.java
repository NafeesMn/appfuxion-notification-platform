package com.appfuxion_notification_platform.backend.delivery.scaling.runtime;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.transaction.annotation.Transactional;
import com.appfuxion_notification_platform.backend.delivery.scaling.PartitionLeaseService;
import com.appfuxion_notification_platform.backend.delivery.scaling.domain.LeaseAcquireResult;
import com.appfuxion_notification_platform.backend.delivery.scaling.domain.PartitionLease;
import com.appfuxion_notification_platform.backend.delivery.scaling.domain.WorkerIdentity;
import com.appfuxion_notification_platform.backend.operations.persistence.WorkerPartitionLease;
import com.appfuxion_notification_platform.backend.operations.persistence.WorkerPartitionLeaseRepository;

@Transactional
public class DatabasePartitionLeaseService implements PartitionLeaseService {

    private final WorkerPartitionLeaseRepository workerPartitionLeaseRepository;

    public DatabasePartitionLeaseService(WorkerPartitionLeaseRepository workerPartitionLeaseRepository) {
        this.workerPartitionLeaseRepository = Objects.requireNonNull(workerPartitionLeaseRepository);
    }

    @Override
    public LeaseAcquireResult tryAcquire(int partitionId, WorkerIdentity worker, Duration leaseTtl) {
        validateCommon(partitionId, worker, leaseTtl);
        String workerId = requireWorkerId(worker);
        Instant now = Instant.now();
        Instant leaseExpiredAt = now.plus(leaseTtl);

        try {
            WorkerPartitionLease lease = workerPartitionLeaseRepository.findById(partitionId).orElse(null);

            if(lease == null){
                WorkerPartitionLease created = new WorkerPartitionLease();
                created.setPartitionId(partitionId);
                created.setWorkerId(workerId);
                created.setHeartbeatAt(now);
                created.setLeaseExpiresAt(leaseExpiredAt);
                WorkerPartitionLease saved = workerPartitionLeaseRepository.save(created);
                return new LeaseAcquireResult(true, toDomain(saved));
            }

            boolean ownedByCaller = workerId.equals(lease.getWorkerId());
            boolean expired = isExpired(lease, now);

            if (ownedByCaller || expired) {
                lease.setWorkerId(workerId);
                lease.setHeartbeatAt(now);
                lease.setLeaseExpiresAt(leaseExpiredAt);
                WorkerPartitionLease saved = workerPartitionLeaseRepository.save(lease);
                return new LeaseAcquireResult(true, toDomain(saved));
            }

            return new LeaseAcquireResult(false, toDomain(lease));
        } catch (ObjectOptimisticLockingFailureException e) {
            return new LeaseAcquireResult(false, null);
        }
    }

    @Override
    public boolean heartbeat(int partitionId, WorkerIdentity worker, Duration leaseTtl) {
        validateCommon(partitionId, worker, leaseTtl);
        String workerId = requireWorkerId(worker);
        Instant now = Instant.now();
        Instant leaseExpiresAt = now.plus(leaseTtl);

        try {
            WorkerPartitionLease lease = workerPartitionLeaseRepository.findById(partitionId).orElse(null);
            if(lease == null) return false;
            if(!workerId.equals(lease.getWorkerId())) return false;

            lease.setHeartbeatAt(now);
            lease.setLeaseExpiresAt(leaseExpiresAt);
            workerPartitionLeaseRepository.save(lease);
            return true;
        } catch (ObjectOptimisticLockingFailureException ex) {
            return false;
        }
    }

    @Override
    public void release(int partitionId, WorkerIdentity worker) {
        Objects.requireNonNull(worker, "worker");
        if (partitionId < 0) {
            throw new IllegalArgumentException("partitionId must be >= 0");
        }

        WorkerPartitionLease lease = workerPartitionLeaseRepository.findById(partitionId).orElse(null);
        if (lease == null) {
            return;
        }

        String workerId = requireWorkerId(worker);
        if (workerId.equals(lease.getWorkerId())) {
            workerPartitionLeaseRepository.delete(lease);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Set<Integer> listOwnedPartitions(WorkerIdentity worker) {
        Objects.requireNonNull(worker, "worker");
        String workerId = requireWorkerId(worker);
        Instant now = Instant.now();

        return workerPartitionLeaseRepository.findAll().stream()
                .filter(lease -> workerId.equals(lease.getWorkerId()))
                .filter(lease -> !isExpired(lease, now))
                .map(WorkerPartitionLease::getPartitionId)
                .collect(Collectors.toUnmodifiableSet());
    }

    @Override
    public int reclaimExpiredLeases(Instant now) {
        Objects.requireNonNull(now, "now");
        var expired = workerPartitionLeaseRepository.findByLeaseExpiresAtBefore(now);
        if(expired.isEmpty()) return 0;
        workerPartitionLeaseRepository.deleteAll(expired);
        return expired.size();
    }

    private static void validateCommon(int partitionId, WorkerIdentity worker, Duration leaseTtl){
        Objects.requireNonNull(worker, "worker");
        Objects.requireNonNull(leaseTtl, "leaseTtl");
        if (partitionId < 0)   
            throw new IllegalArgumentException("partitionId must be >= 0");
        if(leaseTtl.isZero() || leaseTtl.isNegative())
            throw new IllegalArgumentException("leaseTtl must be > 0");
    }

    private String requireWorkerId(WorkerIdentity worker) {
        String workerId = worker.workerId();
        if (workerId == null || workerId.isBlank()) {
            throw new IllegalArgumentException("worker.workerId must not be blank");
        }
        return workerId;
    }

    private boolean isExpired(WorkerPartitionLease lease, Instant now) {
        return !lease.getLeaseExpiresAt().isAfter(now);
    }

    private PartitionLease toDomain(WorkerPartitionLease lease) {
        return new PartitionLease(
            lease.getPartitionId(),
            lease.getWorkerId(),
            lease.getLeaseExpiresAt(),
            lease.getHeartbeatAt(),
            lease.getVersion());
    }
}
