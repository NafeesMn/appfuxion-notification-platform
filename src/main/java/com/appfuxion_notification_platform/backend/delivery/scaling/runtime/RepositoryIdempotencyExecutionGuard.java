package com.appfuxion_notification_platform.backend.delivery.scaling.runtime;

import java.util.Objects;

import com.appfuxion_notification_platform.backend.delivery.domain.NotificationAttemptOutcome;
import com.appfuxion_notification_platform.backend.delivery.persistence.NotificationAttemptRepository;
import com.appfuxion_notification_platform.backend.delivery.persistence.NotificationJob;
import com.appfuxion_notification_platform.backend.delivery.scaling.IdempotencyExecutionGuard;

public class RepositoryIdempotencyExecutionGuard implements IdempotencyExecutionGuard {

    private final NotificationAttemptRepository notificationAttemptRepository;

    public RepositoryIdempotencyExecutionGuard(NotificationAttemptRepository notificationAttemptRepository) {
        this.notificationAttemptRepository = Objects.requireNonNull(notificationAttemptRepository);
    }

    @Override
    public boolean alreadyDelivered(NotificationJob job) {
        Objects.requireNonNull(job, "job");
        if (job.getId() == null) {
            return false;
        }
        return notificationAttemptRepository.existsByNotificationJobIdAndOutcome(
                job.getId(),
                NotificationAttemptOutcome.SUCCESS);
    }
}
