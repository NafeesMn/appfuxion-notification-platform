package com.appfuxion_notification_platform.backend.delivery.persistence;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import com.appfuxion_notification_platform.backend.delivery.domain.NotificationAttemptOutcome;

public interface NotificationAttemptRepository extends JpaRepository<NotificationAttempt, UUID> {

    List<NotificationAttempt> findByNotificationJobIdOrderByAttemptNumberAsc(UUID notificationJobId);

    List<NotificationAttempt> findByCampaignIdAndTenantIdOrderByCreatedAtAsc(UUID campaignId, UUID tenantId);

    boolean existsByNotificationJobIdAndOutcome(UUID notificationJobId, NotificationAttemptOutcome outcome);
}
