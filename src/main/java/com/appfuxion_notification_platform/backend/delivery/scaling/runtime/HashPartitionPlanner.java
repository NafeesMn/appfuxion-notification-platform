package com.appfuxion_notification_platform.backend.delivery.scaling.runtime;

import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

import com.appfuxion_notification_platform.backend.delivery.scaling.PartitionPlanner;
import com.appfuxion_notification_platform.backend.delivery.scaling.domain.WorkerIdentity;

public class HashPartitionPlanner implements PartitionPlanner {

    @Override
    public Set<Integer> desiredPartitionsForWorker(
            WorkerIdentity worker,
            int totalPartitions,
            int activeWorkerCount) {
        Objects.requireNonNull(worker, "worker");

        String workerId = worker.workerId();
        if(workerId == null || workerId.isBlank()){
            throw new IllegalArgumentException("workerId must not be blank");
        }
        
        if (totalPartitions <= 0) {
            throw new IllegalArgumentException("totalPartitions must be > 0");
        }
        if (activeWorkerCount <= 0) {
            throw new IllegalArgumentException("activeWorkerCount must be > 0");
        }

        int workerSlot = Math.floorMod(workerId.hashCode(), activeWorkerCount);
        Set<Integer> assigned = new HashSet<>();

        for(int partitionId = 0; partitionId < totalPartitions; partitionId++) {
            if(partitionId % activeWorkerCount == workerSlot) {
                assigned.add(partitionId);
            }
        }

        if(assigned.isEmpty()){
            assigned.add(workerSlot % totalPartitions);
        }

        return Collections.unmodifiableSet(assigned);
    }
}
