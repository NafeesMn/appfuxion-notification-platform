package com.appfuxion_notification_platform.backend.delivery.scaling.runtime;

import java.time.Instant;
import java.util.Objects;
import java.util.Set;

import com.appfuxion_notification_platform.backend.delivery.domain.NotificationJobStatus;
import com.appfuxion_notification_platform.backend.delivery.persistence.NotificationJob;
import com.appfuxion_notification_platform.backend.delivery.persistence.NotificationJobRepository;
import com.appfuxion_notification_platform.backend.delivery.rules.NotificationRuleContext;
import com.appfuxion_notification_platform.backend.delivery.rules.NotificationRuleContextLoader;
import com.appfuxion_notification_platform.backend.delivery.rules.NotificationRuleDecision;
import com.appfuxion_notification_platform.backend.delivery.rules.NotificationRuleEngine;
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
    private final NotificationRuleContextLoader ruleContextLoader;
    private final NotificationRuleEngine ruleEngine;
    private final boolean ruleEvaluationEnabled;

    public DefaultNotificationJobExecutor(
            GlobalChannelRateLimiter channelRateLimiter,
            ScalingMetricsRecorder metricsRecorder,
            NotificationJobRepository notificationJobRepository) {
        this.channelRateLimiter = Objects.requireNonNull(channelRateLimiter);
        this.metricsRecorder = Objects.requireNonNull(metricsRecorder);
        this.notificationJobRepository = Objects.requireNonNull(notificationJobRepository);
        this.ruleContextLoader = null;
        this.ruleEngine = null;
        this.ruleEvaluationEnabled = false;
    }

    public DefaultNotificationJobExecutor(
            GlobalChannelRateLimiter channelRateLimiter,
            ScalingMetricsRecorder metricsRecorder,
            NotificationJobRepository notificationJobRepository,
            NotificationRuleContextLoader ruleContextLoader,
            NotificationRuleEngine ruleEngine) {
        this.channelRateLimiter = Objects.requireNonNull(channelRateLimiter);
        this.metricsRecorder = Objects.requireNonNull(metricsRecorder);
        this.notificationJobRepository = Objects.requireNonNull(notificationJobRepository);
        this.ruleContextLoader = Objects.requireNonNull(ruleContextLoader);
        this.ruleEngine = Objects.requireNonNull(ruleEngine);
        this.ruleEvaluationEnabled = true;
    }

    @Override
    public void execute(NotificationJob job, WorkerIdentity worker, Instant now) {
        Objects.requireNonNull(job, "job");
        Objects.requireNonNull(worker, "worker");
        Objects.requireNonNull(now, "now");

        if (TERMINAL.contains(job.getStatus())) {
            return;
        }

        if (ruleEvaluationEnabled) {
            NotificationRuleContext context = ruleContextLoader.loadFor(job);
            NotificationRuleDecision ruleDecision = ruleEngine.evaluate(context, now);
            if (applyRuleDecision(job, ruleDecision, now)) {
                notificationJobRepository.save(job);
                return;
            }
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

        // Phase 5 boundary:
        // Rule checks and throttle deferral are now applied.
        // Provider send + attempt persistence + retry transitions continue in Phase 6+.
        job.setStatus(NotificationJobStatus.PROCESSING);
        job.setDeferredUntil(null);
        job.setLastRuleReasonCode(null);
        job.setCompletedAt(null);
        job.setLastAttemptAt(now);
        notificationJobRepository.save(job);
    }

    private boolean applyRuleDecision(NotificationJob job, NotificationRuleDecision ruleDecision, Instant now) {
        Objects.requireNonNull(ruleDecision, "ruleDecision");

        return switch (ruleDecision.action()) {
            case ALLOW -> false;
            case SKIP -> {
                job.setStatus(NotificationJobStatus.SKIPPED);
                job.setLastRuleReasonCode(ruleDecision.reasonCode());
                job.setCompletedAt(now);
                job.setDeferredUntil(null);
                yield true;
            }
            case DELAY -> {
                job.setStatus(NotificationJobStatus.DELAYED);
                job.setDeferredUntil(ruleDecision.nextEligibleAt());
                job.setNextAttemptAt(ruleDecision.nextEligibleAt());
                job.setLastRuleReasonCode(ruleDecision.reasonCode());
                job.setCompletedAt(null);
                yield true;
            }
        };
    }
}
