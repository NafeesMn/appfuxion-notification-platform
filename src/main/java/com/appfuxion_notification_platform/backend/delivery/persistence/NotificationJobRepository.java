package com.appfuxion_notification_platform.backend.delivery.persistence;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.appfuxion_notification_platform.backend.delivery.domain.NotificationJobStatus;

public interface NotificationJobRepository extends JpaRepository<NotificationJob, UUID> {

    @Query("""
        select j
        from NotificationJob j
        where j.status in :statuses
          and j.nextAttemptAt <= :cutoff
        order by j.nextAttemptAt asc, j.createdAt asc
        """)
    List<NotificationJob> findDueJobs(
            @Param("statuses") Collection<NotificationJobStatus> statuses,
            @Param("cutoff") Instant cutoff,
            Pageable pageable);

    @Query("""
        select j
        from NotificationJob j
        where j.status in :statuses
          and j.nextAttemptAt <= :cutoff
          and j.partitionKey in :partitionKeys
        order by j.nextAttemptAt asc, j.createdAt asc
        """)
    List<NotificationJob> findDueJobsForPartitions(
            @Param("statuses") Collection<NotificationJobStatus> statuses,
            @Param("cutoff") Instant cutoff,
            @Param("partitionKeys") Collection<Integer> partitionKeys,
            Pageable pageable);

    List<NotificationJob> findByCampaignIdAndTenantIdAndStatus(UUID campaignId, UUID tenantId, NotificationJobStatus status);

    long countByCampaignIdAndTenantId(UUID campaignId, UUID tenantId);

    long countByCampaignIdAndTenantIdAndStatus(UUID campaignId, UUID tenantId, NotificationJobStatus status);

    long countByCampaignIdAndTenantIdAndStatusIn(
            UUID campaignId,
            UUID tenantId,
            Collection<NotificationJobStatus> statuses);
}
