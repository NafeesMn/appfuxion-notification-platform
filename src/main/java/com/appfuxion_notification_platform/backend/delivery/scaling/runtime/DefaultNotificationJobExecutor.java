package com.appfuxion_notification_platform.backend.delivery.scaling.runtime;

import java.time.Instant;
import java.util.Objects;
import java.util.Set;
import com.appfuxion_notification_platform.backend.delivery.domain.NotificationJobStatus;
import com.appfuxion_notification_platform.backend.delivery.persistence.NotificationJob;
import com.appfuxion_notification_platform.backend.delivery.persistence.NotificationJobRepository;
import com.appfuxion_notification_platform.backend.delivery.scaling.GlobalChannelRateLimiter;
import com.appfuxion_notification_platform.backend.delivery.scaling.NotificationJobExecutor;
import com.appfuxion_notification_platform.backend.delivery.scaling.ScalingMetricsRecorder;
import com.appfuxion_notification_platform.backend.delivery.scaling.domain.BackPressureDecision;
import com.appfuxion_notification_platform.backend.delivery.scaling.domain.WorkerIdentity;

public class DefaultNotificationJobExecutor implements NotificationJobExecutor {

    private static final Set<NotificationJobStatus> TERMINAL = Set.of(
            NotificationJobStatus.SENT,
            NotificationJobStatus.FAILED,
            NotificationJobStatus.SKIPPED);

    private final GlobalChannelRateLimiter channelRateLimiter;
    private final ScalingMetricsRecorder metricsRecorder;
    private final NotificationJobRepository notificationJobRepository;
    public DefaultNotificationJobExecutor(
            GlobalChannelRateLimiter channelRateLimiter,
            ScalingMetricsRecorder metricsRecorder,
            NotificationJobRepository notificationJobRepository) {
        this.channelRateLimiter = Objects.requireNonNull(channelRateLimiter);
        this.metricsRecorder = Objects.requireNonNull(metricsRecorder);
        this.notificationJobRepository = Objects.requireNonNull(notificationJobRepository);
    }

    @Override
    public void execute(NotificationJob job, WorkerIdentity worker, Instant now) {
        Objects.requireNonNull(job, "job");
        Objects.requireNonNull(worker, "worker");
        Objects.requireNonNull(now, "now");

        if (TERMINAL.contains(job.getStatus())) {
            return;
        }

        BackPressureDecision decision = channelRateLimiter.beforeDispatch(job.getChannel(), now);

        if (!decision.allowedNow()) {
            job.setStatus(NotificationJobStatus.DELAYED);
            job.setDeferredUntil(decision.nextEligibleAt());
            job.setNextAttemptAt(decision.nextEligibleAt());
            job.setLastRuleReasonCode(decision.reason());
            metricsRecorder.recordThrottleDeferral(job.getChannel());
            notificationJobRepository.save(job);
            return;
        }

        // Phase 4: claim as processing.
        // Provider send + attempt persistence + retry transitions are added in later phases.
        job.setStatus(NotificationJobStatus.PROCESSING);
        job.setLastAttemptAt(now);
        notificationJobRepository.save(job);
    }
}
