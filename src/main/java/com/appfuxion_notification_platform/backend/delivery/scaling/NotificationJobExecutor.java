package com.appfuxion_notification_platform.backend.delivery.scaling;

import java.time.Instant;

import com.appfuxion_notification_platform.backend.delivery.persistence.NotificationJob;
import com.appfuxion_notification_platform.backend.delivery.scaling.domain.WorkerIdentity;

public interface NotificationJobExecutor {

    void execute(NotificationJob job, WorkerIdentity worker, Instant now);
}
