package com.appfuxion_notification_platform.backend.operations.persistence;

import java.time.Instant;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkerPartitionLeaseRepository extends JpaRepository<WorkerPartitionLease, Integer> {

    List<WorkerPartitionLease> findByLeaseExpiresAtBefore(Instant cutoff);
}
