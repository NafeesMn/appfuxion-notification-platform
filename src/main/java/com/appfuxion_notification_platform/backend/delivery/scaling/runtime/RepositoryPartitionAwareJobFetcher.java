package com.appfuxion_notification_platform.backend.delivery.scaling.runtime;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;
import com.appfuxion_notification_platform.backend.delivery.domain.NotificationJobStatus;
import com.appfuxion_notification_platform.backend.delivery.persistence.NotificationJob;
import com.appfuxion_notification_platform.backend.delivery.persistence.NotificationJobRepository;
import com.appfuxion_notification_platform.backend.delivery.scaling.PartitionAwareJobFetcher;

public class RepositoryPartitionAwareJobFetcher implements PartitionAwareJobFetcher {

    private static final List<NotificationJobStatus> DUE_STATUSES = List.of(
            NotificationJobStatus.PENDING,
            NotificationJobStatus.RETRY_SCHEDULED,
            NotificationJobStatus.DELAYED);

    private final NotificationJobRepository notificationJobRepository;

    public RepositoryPartitionAwareJobFetcher(NotificationJobRepository notificationJobRepository) {
        this.notificationJobRepository = Objects.requireNonNull(notificationJobRepository);
    }

    @Override
    @Transactional
    public List<NotificationJob> fetchDueJobs(Set<Integer> partitions, Instant now, int batchSize) {
        Objects.requireNonNull(partitions, "partitions");
        Objects.requireNonNull(now, "now");
        if (batchSize <= 0) {
            throw new IllegalArgumentException("batchSize must be > 0");
        }
        if (partitions.isEmpty()) {
            return List.of();
        }

        List<NotificationJob> due = notificationJobRepository.findDueJobsForPartitions(
                DUE_STATUSES,
                now,
                partitions,
                PageRequest.of(0, batchSize));
        
        for(NotificationJob job : due) {
            // claim then move to PROCESSING now
            job.setStatus(NotificationJobStatus.PROCESSING);
            job.setLastAttemptAt(now);
        }

        return notificationJobRepository.saveAll(due);
    }

    // Optional helper for later explicit requeue on lease loss/failure.
    public void requeueAsPending(UUID jobId, Instant nextAttemptAt) {
        NotificationJob job = notificationJobRepository.findById(jobId)
                .orElseThrow(() -> new IllegalArgumentException("NotificationJob not found: " + jobId));
        job.setStatus(NotificationJobStatus.PENDING);
        job.setNextAttemptAt(nextAttemptAt);
        notificationJobRepository.save(job);
    }
}
