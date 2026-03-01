package com.appfuxion_notification_platform.backend.delivery.scaling;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import com.appfuxion_notification_platform.backend.delivery.persistence.NotificationJob;

public interface PartitionAwareJobFetcher {

    List<NotificationJob> fetchDueJobs(Set<Integer> partitions, Instant now, int batchSize);
}
