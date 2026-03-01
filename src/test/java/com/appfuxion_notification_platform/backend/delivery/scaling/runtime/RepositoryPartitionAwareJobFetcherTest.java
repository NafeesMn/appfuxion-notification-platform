package com.appfuxion_notification_platform.backend.delivery.scaling.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import com.appfuxion_notification_platform.backend.delivery.domain.NotificationJobStatus;
import com.appfuxion_notification_platform.backend.delivery.persistence.NotificationJob;
import com.appfuxion_notification_platform.backend.delivery.persistence.NotificationJobRepository;

class RepositoryPartitionAwareJobFetcherTest {

    @Test
    void fetchDueJobs_shouldClaimJobsAsProcessingAndUpdateLastAttemptAt() {
        NotificationJobRepository notificationJobRepository = Mockito.mock(NotificationJobRepository.class);
        RepositoryPartitionAwareJobFetcher fetcher = new RepositoryPartitionAwareJobFetcher(notificationJobRepository);
        Instant now = Instant.parse("2026-03-01T00:00:00Z");

        NotificationJob first = new NotificationJob();
        first.setStatus(NotificationJobStatus.PENDING);
        NotificationJob second = new NotificationJob();
        second.setStatus(NotificationJobStatus.RETRY_SCHEDULED);
        List<NotificationJob> dueJobs = List.of(first, second);

        when(notificationJobRepository.findDueJobsForPartitions(any(), eq(now), eq(Set.of(1, 2)), any()))
                .thenReturn(dueJobs);
        when(notificationJobRepository.saveAll(dueJobs)).thenReturn(dueJobs);

        List<NotificationJob> claimed = fetcher.fetchDueJobs(Set.of(1, 2), now, 10);

        assertEquals(2, claimed.size());
        assertTrue(claimed.stream().allMatch(job -> job.getStatus() == NotificationJobStatus.PROCESSING));
        assertTrue(claimed.stream().allMatch(job -> now.equals(job.getLastAttemptAt())));
        verify(notificationJobRepository).saveAll(dueJobs);
    }

    @Test
    void fetchDueJobs_shouldShortCircuitWhenNoPartitionsOwned() {
        NotificationJobRepository notificationJobRepository = Mockito.mock(NotificationJobRepository.class);
        RepositoryPartitionAwareJobFetcher fetcher = new RepositoryPartitionAwareJobFetcher(notificationJobRepository);

        List<NotificationJob> result = fetcher.fetchDueJobs(Set.of(), Instant.parse("2026-03-01T00:00:00Z"), 10);

        assertTrue(result.isEmpty());
        verify(notificationJobRepository, never()).findDueJobsForPartitions(any(), any(), any(), any());
    }
}
