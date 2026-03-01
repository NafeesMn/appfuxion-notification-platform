package com.appfuxion_notification_platform.backend.delivery.scaling.runtime;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Set;

import com.appfuxion_notification_platform.backend.delivery.domain.NotificationAttemptOutcome;
import com.appfuxion_notification_platform.backend.delivery.domain.NotificationJobStatus;
import com.appfuxion_notification_platform.backend.delivery.persistence.NotificationAttempt;
import com.appfuxion_notification_platform.backend.delivery.persistence.NotificationAttemptRepository;
import com.appfuxion_notification_platform.backend.delivery.persistence.NotificationJob;
import com.appfuxion_notification_platform.backend.delivery.persistence.NotificationJobRepository;
import com.appfuxion_notification_platform.backend.delivery.rules.NotificationRuleContext;
import com.appfuxion_notification_platform.backend.delivery.rules.NotificationRuleContextLoader;
import com.appfuxion_notification_platform.backend.delivery.rules.NotificationRuleDecision;
import com.appfuxion_notification_platform.backend.delivery.scaling.IdempotencyExecutionGuard;
import com.appfuxion_notification_platform.backend.delivery.rules.NotificationRuleEngine;
import com.appfuxion_notification_platform.backend.delivery.scaling.GlobalChannelRateLimiter;
import com.appfuxion_notification_platform.backend.delivery.scaling.NotificationProviderGateway;
import com.appfuxion_notification_platform.backend.delivery.scaling.NotificationJobExecutor;
import com.appfuxion_notification_platform.backend.delivery.scaling.RetryBackoffPolicy;
import com.appfuxion_notification_platform.backend.delivery.scaling.ScalingMetricsRecorder;
import com.appfuxion_notification_platform.backend.delivery.scaling.domain.BackPressureDecision;
import com.appfuxion_notification_platform.backend.delivery.scaling.domain.ProviderDispatchResult;
import com.appfuxion_notification_platform.backend.delivery.scaling.domain.WorkerIdentity;

public class DefaultNotificationJobExecutor implements NotificationJobExecutor {

    private static final Set<NotificationJobStatus> TERMINAL = Set.of(
            NotificationJobStatus.SENT,
            NotificationJobStatus.FAILED,
            NotificationJobStatus.SKIPPED);

    private final GlobalChannelRateLimiter channelRateLimiter;
    private final ScalingMetricsRecorder metricsRecorder;
    private final NotificationJobRepository notificationJobRepository;
    private final NotificationAttemptRepository notificationAttemptRepository;
    private final NotificationProviderGateway providerGateway;
    private final RetryBackoffPolicy retryBackoffPolicy;
    private final IdempotencyExecutionGuard idempotencyExecutionGuard;
    private final NotificationRuleContextLoader ruleContextLoader;
    private final NotificationRuleEngine ruleEngine;
    private final boolean ruleEvaluationEnabled;
    private final boolean deliveryExecutionEnabled;

    public DefaultNotificationJobExecutor(
            GlobalChannelRateLimiter channelRateLimiter,
            ScalingMetricsRecorder metricsRecorder,
            NotificationJobRepository notificationJobRepository) {
        this.channelRateLimiter = Objects.requireNonNull(channelRateLimiter);
        this.metricsRecorder = Objects.requireNonNull(metricsRecorder);
        this.notificationJobRepository = Objects.requireNonNull(notificationJobRepository);
        this.notificationAttemptRepository = null;
        this.providerGateway = null;
        this.retryBackoffPolicy = null;
        this.idempotencyExecutionGuard = null;
        this.ruleContextLoader = null;
        this.ruleEngine = null;
        this.ruleEvaluationEnabled = false;
        this.deliveryExecutionEnabled = false;
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
        this.notificationAttemptRepository = null;
        this.providerGateway = null;
        this.retryBackoffPolicy = null;
        this.idempotencyExecutionGuard = null;
        this.ruleContextLoader = Objects.requireNonNull(ruleContextLoader);
        this.ruleEngine = Objects.requireNonNull(ruleEngine);
        this.ruleEvaluationEnabled = true;
        this.deliveryExecutionEnabled = false;
    }

    public DefaultNotificationJobExecutor(
            GlobalChannelRateLimiter channelRateLimiter,
            ScalingMetricsRecorder metricsRecorder,
            NotificationJobRepository notificationJobRepository,
            NotificationAttemptRepository notificationAttemptRepository,
            NotificationProviderGateway providerGateway,
            RetryBackoffPolicy retryBackoffPolicy,
            IdempotencyExecutionGuard idempotencyExecutionGuard,
            NotificationRuleContextLoader ruleContextLoader,
            NotificationRuleEngine ruleEngine) {
        this.channelRateLimiter = Objects.requireNonNull(channelRateLimiter);
        this.metricsRecorder = Objects.requireNonNull(metricsRecorder);
        this.notificationJobRepository = Objects.requireNonNull(notificationJobRepository);
        this.notificationAttemptRepository = Objects.requireNonNull(notificationAttemptRepository);
        this.providerGateway = Objects.requireNonNull(providerGateway);
        this.retryBackoffPolicy = Objects.requireNonNull(retryBackoffPolicy);
        this.idempotencyExecutionGuard = Objects.requireNonNull(idempotencyExecutionGuard);
        this.ruleContextLoader = Objects.requireNonNull(ruleContextLoader);
        this.ruleEngine = Objects.requireNonNull(ruleEngine);
        this.ruleEvaluationEnabled = true;
        this.deliveryExecutionEnabled = true;
    }

    @Override
    public void execute(NotificationJob job, WorkerIdentity worker, Instant now) {
        Objects.requireNonNull(job, "job");
        Objects.requireNonNull(worker, "worker");
        Objects.requireNonNull(now, "now");

        if (TERMINAL.contains(job.getStatus())) {
            return;
        }

        recordLagIfAny(job, now);

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
            job.setCompletedAt(null);
            metricsRecorder.recordThrottleDeferral(job.getChannel());
            notificationJobRepository.save(job);
            return;
        }

        if (!deliveryExecutionEnabled) {
            // Phase 5 boundary:
            // Rule checks and throttle deferral are applied.
            // Provider send + attempt persistence + retry transitions are enabled by the Phase 6 constructor.
            job.setStatus(NotificationJobStatus.PROCESSING);
            job.setDeferredUntil(null);
            job.setLastRuleReasonCode(null);
            job.setCompletedAt(null);
            job.setLastAttemptAt(now);
            notificationJobRepository.save(job);
            return;
        }

        executeDelivery(job, worker, now);
    }

    private void executeDelivery(NotificationJob job, WorkerIdentity worker, Instant now) {
        if (idempotencyExecutionGuard.alreadyDelivered(job)) {
            job.setStatus(NotificationJobStatus.SENT);
            job.setCompletedAt(now);
            job.setDeferredUntil(null);
            job.setLastRuleReasonCode(null);
            job.setLastErrorCode(null);
            job.setLastErrorMessage(null);
            notificationJobRepository.save(job);
            return;
        }

        job.setStatus(NotificationJobStatus.PROCESSING);
        job.setLastAttemptAt(now);
        job.setDeferredUntil(null);
        job.setLastRuleReasonCode(null);
        job.setCompletedAt(null);

        int nextAttemptNumber = job.getAttemptCount() + 1;
        ProviderDispatchResult dispatchResult;
        try {
            dispatchResult = providerGateway.dispatch(job, worker);
        } catch (RuntimeException ex) {
            dispatchResult = ProviderDispatchResult.retryableFailure(
                    "EXECUTION_EXCEPTION",
                    ex.getMessage(),
                    null,
                    null,
                    null,
                    null,
                    null);
        }

        notificationAttemptRepository.save(buildAttempt(job, worker, now, nextAttemptNumber, dispatchResult));
        applyDispatchResult(job, now, nextAttemptNumber, dispatchResult);
        // Phase 5 boundary:
        // Rule checks and throttle deferral are applied.
        // Provider dispatch + retries/backoff + idempotency guard are now applied.
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
                job.setLastErrorCode(null);
                job.setLastErrorMessage(null);
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

    private NotificationAttempt buildAttempt(
            NotificationJob job,
            WorkerIdentity worker,
            Instant now,
            int attemptNumber,
            ProviderDispatchResult dispatchResult) {
        NotificationAttempt attempt = new NotificationAttempt();
        attempt.setTenantId(job.getTenantId());
        attempt.setCampaignId(job.getCampaignId());
        attempt.setNotificationJobId(job.getId());
        attempt.setAttemptNumber(attemptNumber);
        attempt.setWorkerId(worker.workerId());
        attempt.setPartitionId(job.getPartitionKey());
        attempt.setOutcome(dispatchResult.outcome());
        attempt.setErrorCode(dispatchResult.errorCode());
        attempt.setProviderRequestId(dispatchResult.providerRequestId());
        attempt.setProviderResponseCode(dispatchResult.providerResponseCode());
        attempt.setLatencyMs(dispatchResult.latencyMs());
        attempt.setRequestMetadata(dispatchResult.requestMetadata());
        attempt.setResponseMetadata(dispatchResult.responseMetadata());
        attempt.setStartedAt(now);
        attempt.setCompletedAt(now);
        return attempt;
    }

    private void applyDispatchResult(
            NotificationJob job,
            Instant now,
            int nextAttemptNumber,
            ProviderDispatchResult dispatchResult) {
        job.setAttemptCount(nextAttemptNumber);

        if (dispatchResult.outcome() == NotificationAttemptOutcome.SUCCESS) {
            job.setStatus(NotificationJobStatus.SENT);
            job.setCompletedAt(now);
            job.setDeferredUntil(null);
            job.setLastErrorCode(null);
            job.setLastErrorMessage(null);
            job.setLastRuleReasonCode(null);
            return;
        }

        if (dispatchResult.outcome() == NotificationAttemptOutcome.TERMINAL_FAILURE) {
            markFailed(job, now, dispatchResult.errorCode(), dispatchResult.errorMessage());
            return;
        }

        if (dispatchResult.outcome() == NotificationAttemptOutcome.RETRYABLE_FAILURE) {
            if (nextAttemptNumber > job.getMaxRetries()) {
                markFailed(
                        job,
                        now,
                        "MAX_RETRIES_EXCEEDED",
                        firstNonBlank(dispatchResult.errorMessage(), "Retry budget exhausted"));
                return;
            }
            Instant retryAt = retryBackoffPolicy.nextAttemptAt(nextAttemptNumber, now);
            job.setStatus(NotificationJobStatus.RETRY_SCHEDULED);
            job.setNextAttemptAt(retryAt);
            job.setDeferredUntil(null);
            job.setCompletedAt(null);
            job.setLastErrorCode(firstNonBlank(dispatchResult.errorCode(), "RETRYABLE_FAILURE"));
            job.setLastErrorMessage(dispatchResult.errorMessage());
            return;
        }

        markFailed(job, now, "UNSUPPORTED_OUTCOME", "Unsupported dispatch outcome: " + dispatchResult.outcome());
    }

    private void markFailed(NotificationJob job, Instant now, String errorCode, String errorMessage) {
        job.setStatus(NotificationJobStatus.FAILED);
        job.setCompletedAt(now);
        job.setDeferredUntil(null);
        job.setLastErrorCode(firstNonBlank(errorCode, "FAILED"));
        job.setLastErrorMessage(firstNonBlank(errorMessage, "Dispatch failed"));
    }

    private String firstNonBlank(String value, String fallback) {
        if (value != null && !value.isBlank()) {
            return value;
        }
        return fallback;
    }

    private void recordLagIfAny(NotificationJob job, Instant now) {
        if (job.getNextAttemptAt() == null) {
            return;
        }
        long lagSeconds = Duration.between(job.getNextAttemptAt(), now).getSeconds();
        if (lagSeconds > 0) {
            metricsRecorder.recordJobLagSeconds(lagSeconds);
        }
    }
}
