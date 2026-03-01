package com.appfuxion_notification_platform.backend.delivery.scaling.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import com.appfuxion_notification_platform.backend.delivery.domain.NotificationAttemptOutcome;
import com.appfuxion_notification_platform.backend.delivery.domain.NotificationJobStatus;
import com.appfuxion_notification_platform.backend.delivery.persistence.NotificationAttempt;
import com.appfuxion_notification_platform.backend.delivery.persistence.NotificationAttemptRepository;
import com.appfuxion_notification_platform.backend.delivery.persistence.NotificationJob;
import com.appfuxion_notification_platform.backend.delivery.persistence.NotificationJobRepository;
import com.appfuxion_notification_platform.backend.delivery.rules.NotificationRuleContext;
import com.appfuxion_notification_platform.backend.delivery.rules.NotificationRuleContextLoader;
import com.appfuxion_notification_platform.backend.delivery.rules.NotificationRuleDecision;
import com.appfuxion_notification_platform.backend.delivery.rules.NotificationRuleEngine;
import com.appfuxion_notification_platform.backend.delivery.scaling.GlobalChannelRateLimiter;
import com.appfuxion_notification_platform.backend.delivery.scaling.IdempotencyExecutionGuard;
import com.appfuxion_notification_platform.backend.delivery.scaling.NotificationProviderGateway;
import com.appfuxion_notification_platform.backend.delivery.scaling.RetryBackoffPolicy;
import com.appfuxion_notification_platform.backend.delivery.scaling.ScalingMetricsRecorder;
import com.appfuxion_notification_platform.backend.delivery.scaling.domain.BackPressureDecision;
import com.appfuxion_notification_platform.backend.delivery.scaling.domain.ProviderDispatchResult;
import com.appfuxion_notification_platform.backend.delivery.scaling.domain.WorkerIdentity;
import com.appfuxion_notification_platform.backend.domain.shared.NotificationChannel;

class DefaultNotificationJobExecutorPhase6Test {

    @Test
    void execute_shouldPersistSuccessAttemptAndMarkSent() {
        Fixture fixture = new Fixture();
        NotificationJob job = fixture.pendingJob();
        Instant now = Instant.parse("2026-03-01T12:00:00Z");

        when(fixture.rateLimiter.beforeDispatch(NotificationChannel.SMS, now))
                .thenReturn(new BackPressureDecision(true, now, "ALLOWED"));
        when(fixture.contextLoader.loadFor(job)).thenReturn(fixture.context);
        when(fixture.ruleEngine.evaluate(fixture.context, now)).thenReturn(NotificationRuleDecision.allow());
        when(fixture.idempotencyExecutionGuard.alreadyDelivered(job)).thenReturn(false);
        when(fixture.providerGateway.dispatch(job, fixture.worker))
                .thenReturn(ProviderDispatchResult.success(
                        "req-1",
                        "200_OK",
                        55,
                        "{\"channel\":\"SMS\"}",
                        "{\"accepted\":true}"));

        fixture.executor.execute(job, fixture.worker, now);

        assertEquals(NotificationJobStatus.SENT, job.getStatus());
        assertEquals(1, job.getAttemptCount());
        assertEquals(now, job.getCompletedAt());
        assertNull(job.getLastErrorCode());

        ArgumentCaptor<NotificationAttempt> attemptCaptor = ArgumentCaptor.forClass(NotificationAttempt.class);
        verify(fixture.attemptRepository).save(attemptCaptor.capture());
        assertEquals(1, attemptCaptor.getValue().getAttemptNumber());
        assertEquals(NotificationAttemptOutcome.SUCCESS, attemptCaptor.getValue().getOutcome());
        verify(fixture.jobRepository).save(job);
    }

    @Test
    void execute_shouldScheduleRetryWithBackoffOnRetryableFailure() {
        Fixture fixture = new Fixture();
        NotificationJob job = fixture.pendingJob();
        Instant now = Instant.parse("2026-03-01T12:00:00Z");
        Instant retryAt = Instant.parse("2026-03-01T12:00:30Z");

        when(fixture.rateLimiter.beforeDispatch(NotificationChannel.SMS, now))
                .thenReturn(new BackPressureDecision(true, now, "ALLOWED"));
        when(fixture.contextLoader.loadFor(job)).thenReturn(fixture.context);
        when(fixture.ruleEngine.evaluate(fixture.context, now)).thenReturn(NotificationRuleDecision.allow());
        when(fixture.idempotencyExecutionGuard.alreadyDelivered(job)).thenReturn(false);
        when(fixture.providerGateway.dispatch(job, fixture.worker))
                .thenReturn(ProviderDispatchResult.retryableFailure(
                        "PROVIDER_TEMPORARY_UNAVAILABLE",
                        "Temporary failure",
                        "req-2",
                        "503_UNAVAILABLE",
                        77,
                        "{}",
                        "{}"));
        when(fixture.retryBackoffPolicy.nextAttemptAt(1, now)).thenReturn(retryAt);

        fixture.executor.execute(job, fixture.worker, now);

        assertEquals(NotificationJobStatus.RETRY_SCHEDULED, job.getStatus());
        assertEquals(1, job.getAttemptCount());
        assertEquals(retryAt, job.getNextAttemptAt());
        assertEquals("PROVIDER_TEMPORARY_UNAVAILABLE", job.getLastErrorCode());
        verify(fixture.retryBackoffPolicy).nextAttemptAt(1, now);
        verify(fixture.jobRepository).save(job);
    }

    @Test
    void execute_shouldMarkFailedWhenRetryBudgetExceeded() {
        Fixture fixture = new Fixture();
        NotificationJob job = fixture.pendingJob();
        job.setAttemptCount(3);
        job.setMaxRetries(3);
        Instant now = Instant.parse("2026-03-01T12:00:00Z");

        when(fixture.rateLimiter.beforeDispatch(NotificationChannel.SMS, now))
                .thenReturn(new BackPressureDecision(true, now, "ALLOWED"));
        when(fixture.contextLoader.loadFor(job)).thenReturn(fixture.context);
        when(fixture.ruleEngine.evaluate(fixture.context, now)).thenReturn(NotificationRuleDecision.allow());
        when(fixture.idempotencyExecutionGuard.alreadyDelivered(job)).thenReturn(false);
        when(fixture.providerGateway.dispatch(job, fixture.worker))
                .thenReturn(ProviderDispatchResult.retryableFailure(
                        "PROVIDER_TEMPORARY_UNAVAILABLE",
                        "Temporary failure",
                        "req-3",
                        "503_UNAVAILABLE",
                        80,
                        "{}",
                        "{}"));

        fixture.executor.execute(job, fixture.worker, now);

        assertEquals(NotificationJobStatus.FAILED, job.getStatus());
        assertEquals(4, job.getAttemptCount());
        assertEquals("MAX_RETRIES_EXCEEDED", job.getLastErrorCode());
        assertEquals(now, job.getCompletedAt());
        verify(fixture.retryBackoffPolicy, never()).nextAttemptAt(anyInt(), any(Instant.class));
        verify(fixture.jobRepository).save(job);
    }

    @Test
    void execute_shouldShortCircuitWhenIdempotencyGuardDetectsDelivered() {
        Fixture fixture = new Fixture();
        NotificationJob job = fixture.pendingJob();
        Instant now = Instant.parse("2026-03-01T12:00:00Z");

        when(fixture.rateLimiter.beforeDispatch(NotificationChannel.SMS, now))
                .thenReturn(new BackPressureDecision(true, now, "ALLOWED"));
        when(fixture.contextLoader.loadFor(job)).thenReturn(fixture.context);
        when(fixture.ruleEngine.evaluate(fixture.context, now)).thenReturn(NotificationRuleDecision.allow());
        when(fixture.idempotencyExecutionGuard.alreadyDelivered(job)).thenReturn(true);

        fixture.executor.execute(job, fixture.worker, now);

        assertEquals(NotificationJobStatus.SENT, job.getStatus());
        assertEquals(now, job.getCompletedAt());
        verify(fixture.providerGateway, never()).dispatch(any(), any());
        verify(fixture.attemptRepository, never()).save(any(NotificationAttempt.class));
        verify(fixture.jobRepository).save(job);
    }

    private static final class Fixture {
        private final GlobalChannelRateLimiter rateLimiter = Mockito.mock(GlobalChannelRateLimiter.class);
        private final ScalingMetricsRecorder metricsRecorder = Mockito.mock(ScalingMetricsRecorder.class);
        private final NotificationJobRepository jobRepository = Mockito.mock(NotificationJobRepository.class);
        private final NotificationAttemptRepository attemptRepository = Mockito.mock(NotificationAttemptRepository.class);
        private final NotificationProviderGateway providerGateway = Mockito.mock(NotificationProviderGateway.class);
        private final RetryBackoffPolicy retryBackoffPolicy = Mockito.mock(RetryBackoffPolicy.class);
        private final IdempotencyExecutionGuard idempotencyExecutionGuard = Mockito.mock(IdempotencyExecutionGuard.class);
        private final NotificationRuleContextLoader contextLoader = Mockito.mock(NotificationRuleContextLoader.class);
        private final NotificationRuleEngine ruleEngine = Mockito.mock(NotificationRuleEngine.class);
        private final WorkerIdentity worker = new WorkerIdentity("worker-phase6");
        private final NotificationRuleContext context = Mockito.mock(NotificationRuleContext.class);
        private final DefaultNotificationJobExecutor executor = new DefaultNotificationJobExecutor(
                rateLimiter,
                metricsRecorder,
                jobRepository,
                attemptRepository,
                providerGateway,
                retryBackoffPolicy,
                idempotencyExecutionGuard,
                contextLoader,
                ruleEngine);

        private NotificationJob pendingJob() {
            NotificationJob job = new NotificationJob();
            job.setTenantId(UUID.randomUUID());
            job.setCampaignId(UUID.randomUUID());
            job.setCampaignRecipientId(UUID.randomUUID());
            job.setCorrelationId(UUID.randomUUID());
            job.setChannel(NotificationChannel.SMS);
            job.setStatus(NotificationJobStatus.PENDING);
            job.setIdempotencyKey("idem-key-" + UUID.randomUUID());
            job.setPartitionKey(4);
            job.setAttemptCount(0);
            job.setMaxRetries(3);
            job.setNextAttemptAt(Instant.parse("2026-03-01T11:59:00Z"));
            return job;
        }
    }
}
