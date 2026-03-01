package com.appfuxion_notification_platform.backend.delivery.scaling.runtime;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;

import org.junit.jupiter.api.Test;

import com.appfuxion_notification_platform.backend.delivery.scaling.domain.WorkerIdentity;

class HashPartitionPlannerTest {

    private final HashPartitionPlanner planner = new HashPartitionPlanner();

    @Test
    void desiredPartitionsForWorker_shouldReturnStableAssignmentForSameInputs() {
        WorkerIdentity worker = new WorkerIdentity("worker-a");

        Set<Integer> first = planner.desiredPartitionsForWorker(worker, 32, 4);
        Set<Integer> second = planner.desiredPartitionsForWorker(worker, 32, 4);

        assertFalse(first.isEmpty());
        assertTrue(first.equals(second));
    }

    @Test
    void desiredPartitionsForWorker_shouldAlwaysAssignAtLeastOnePartition() {
        Set<Integer> assigned = planner.desiredPartitionsForWorker(
                new WorkerIdentity("worker-a"),
                2,
                10);

        assertFalse(assigned.isEmpty());
        assertTrue(assigned.stream().allMatch(partition -> partition >= 0 && partition < 2));
    }
}
