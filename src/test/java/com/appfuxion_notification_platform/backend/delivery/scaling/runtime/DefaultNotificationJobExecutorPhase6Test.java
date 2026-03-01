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
import org.springframework.test.util.ReflectionTestUtils;

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
    void execute_shouldMarkSentAndPersistAttempt_whenProviderReturnsSuccess() {
        Fixture fixture = new Fixture();
        when(fixture.providerGateway.dispatch(fixture.job, fixture.worker))
                .thenReturn(ProviderDispatchResult.success("provider-1", "200_OK", 25, "{}", "{\"result\":\"ok\"}"));

        fixture.executor.execute(fixture.job, fixture.worker, fixture.now);

        assertEquals(NotificationJobStatus.SENT, fixture.job.getStatus());
        assertEquals(1, fixture.job.getAttemptCount());
        assertEquals(fixture.now, fixture.job.getCompletedAt());
        assertNull(fixture.job.getLastErrorCode());
        verify(fixture.notificationJobRepository).save(fixture.job);

        NotificationAttempt attempt = fixture.captureSavedAttempt();
        assertEquals(NotificationAttemptOutcome.SUCCESS, attempt.getOutcome());
        assertEquals(1, attempt.getAttemptNumber());
        assertEquals(fixture.job.getId(), attempt.getNotificationJobId());
    }

    @Test
    void execute_shouldScheduleRetry_whenProviderReturnsRetryableFailureWithinBudget() {
        Fixture fixture = new Fixture();
        Instant retryAt = fixture.now.plusSeconds(45);
        when(fixture.providerGateway.dispatch(fixture.job, fixture.worker))
                .thenReturn(ProviderDispatchResult.retryableFailure(
                        "PROVIDER_TEMP",
                        "temporary",
                        "provider-2",
                        "503_UNAVAILABLE",
                        40,
                        "{}",
                        "{\"result\":\"retry\"}"));
        when(fixture.retryBackoffPolicy.nextAttemptAt(1, fixture.now)).thenReturn(retryAt);

        fixture.executor.execute(fixture.job, fixture.worker, fixture.now);

        assertEquals(NotificationJobStatus.RETRY_SCHEDULED, fixture.job.getStatus());
        assertEquals(1, fixture.job.getAttemptCount());
        assertEquals(retryAt, fixture.job.getNextAttemptAt());
        assertNull(fixture.job.getCompletedAt());
        assertEquals("PROVIDER_TEMP", fixture.job.getLastErrorCode());
        verify(fixture.retryBackoffPolicy).nextAttemptAt(1, fixture.now);

        NotificationAttempt attempt = fixture.captureSavedAttempt();
        assertEquals(NotificationAttemptOutcome.RETRYABLE_FAILURE, attempt.getOutcome());
    }

    @Test
    void execute_shouldFailWhenRetryBudgetExceeded() {
        Fixture fixture = new Fixture();
        fixture.job.setAttemptCount(3);
        fixture.job.setMaxRetries(3);
        when(fixture.providerGateway.dispatch(fixture.job, fixture.worker))
                .thenReturn(ProviderDispatchResult.retryableFailure(
                        "PROVIDER_TEMP",
                        "temporary",
                        "provider-3",
                        "503_UNAVAILABLE",
                        40,
                        "{}",
                        "{\"result\":\"retry\"}"));

        fixture.executor.execute(fixture.job, fixture.worker, fixture.now);

        assertEquals(NotificationJobStatus.FAILED, fixture.job.getStatus());
        assertEquals(4, fixture.job.getAttemptCount());
        assertEquals(fixture.now, fixture.job.getCompletedAt());
        assertEquals("MAX_RETRIES_EXCEEDED", fixture.job.getLastErrorCode());
        verify(fixture.retryBackoffPolicy, never()).nextAttemptAt(anyInt(), any(Instant.class));

        NotificationAttempt attempt = fixture.captureSavedAttempt();
        assertEquals(NotificationAttemptOutcome.RETRYABLE_FAILURE, attempt.getOutcome());
        assertEquals(4, attempt.getAttemptNumber());
    }

    @Test
    void execute_shouldFailImmediately_whenProviderReturnsTerminalFailure() {
        Fixture fixture = new Fixture();
        when(fixture.providerGateway.dispatch(fixture.job, fixture.worker))
                .thenReturn(ProviderDispatchResult.terminalFailure(
                        "INVALID_RECIPIENT",
                        "recipient rejected",
                        "provider-4",
                        "422_INVALID",
                        30,
                        "{}",
                        "{\"result\":\"terminal\"}"));

        fixture.executor.execute(fixture.job, fixture.worker, fixture.now);

        assertEquals(NotificationJobStatus.FAILED, fixture.job.getStatus());
        assertEquals(1, fixture.job.getAttemptCount());
        assertEquals(fixture.now, fixture.job.getCompletedAt());
        assertEquals("INVALID_RECIPIENT", fixture.job.getLastErrorCode());

        NotificationAttempt attempt = fixture.captureSavedAttempt();
        assertEquals(NotificationAttemptOutcome.TERMINAL_FAILURE, attempt.getOutcome());
    }

    @Test
    void execute_shouldShortCircuitAsSent_whenIdempotencyGuardDetectsPriorDelivery() {
        Fixture fixture = new Fixture();
        when(fixture.idempotencyExecutionGuard.alreadyDelivered(fixture.job)).thenReturn(true);

        fixture.executor.execute(fixture.job, fixture.worker, fixture.now);

        assertEquals(NotificationJobStatus.SENT, fixture.job.getStatus());
        assertEquals(fixture.now, fixture.job.getCompletedAt());
        verify(fixture.providerGateway, never()).dispatch(any(), any());
        verify(fixture.notificationAttemptRepository, never()).save(any());
        verify(fixture.notificationJobRepository).save(fixture.job);
    }

    @Test
    void execute_shouldConvertProviderExceptionIntoRetryableFailure() {
        Fixture fixture = new Fixture();
        Instant retryAt = fixture.now.plusSeconds(20);
        when(fixture.providerGateway.dispatch(fixture.job, fixture.worker))
                .thenThrow(new RuntimeException("network timeout"));
        when(fixture.retryBackoffPolicy.nextAttemptAt(1, fixture.now)).thenReturn(retryAt);

        fixture.executor.execute(fixture.job, fixture.worker, fixture.now);

        assertEquals(NotificationJobStatus.RETRY_SCHEDULED, fixture.job.getStatus());
        assertEquals("EXECUTION_EXCEPTION", fixture.job.getLastErrorCode());
        assertEquals("network timeout", fixture.job.getLastErrorMessage());
        assertEquals(retryAt, fixture.job.getNextAttemptAt());

        NotificationAttempt attempt = fixture.captureSavedAttempt();
        assertEquals(NotificationAttemptOutcome.RETRYABLE_FAILURE, attempt.getOutcome());
        assertEquals("EXECUTION_EXCEPTION", attempt.getErrorCode());
    }

    private static NotificationJob buildPendingJob(Instant now) {
        NotificationJob job = new NotificationJob();
        ReflectionTestUtils.setField(job, "id", UUID.randomUUID());
        job.setTenantId(UUID.randomUUID());
        job.setCampaignId(UUID.randomUUID());
        job.setCampaignRecipientId(UUID.randomUUID());
        job.setCorrelationId(UUID.randomUUID());
        job.setChannel(NotificationChannel.SMS);
        job.setStatus(NotificationJobStatus.PENDING);
        job.setIdempotencyKey("idem-123");
        job.setPartitionKey(7);
        job.setAttemptCount(0);
        job.setMaxRetries(3);
        job.setNextAttemptAt(now);
        return job;
    }

    private static final class Fixture {
        private final Instant now = Instant.parse("2026-03-01T12:00:00Z");
        private final WorkerIdentity worker = new WorkerIdentity("worker-phase6");
        private final NotificationJob job = buildPendingJob(now);
        private final GlobalChannelRateLimiter channelRateLimiter = Mockito.mock(GlobalChannelRateLimiter.class);
        private final ScalingMetricsRecorder metricsRecorder = Mockito.mock(ScalingMetricsRecorder.class);
        private final NotificationJobRepository notificationJobRepository = Mockito.mock(NotificationJobRepository.class);
        private final NotificationAttemptRepository notificationAttemptRepository = Mockito.mock(NotificationAttemptRepository.class);
        private final NotificationProviderGateway providerGateway = Mockito.mock(NotificationProviderGateway.class);
        private final RetryBackoffPolicy retryBackoffPolicy = Mockito.mock(RetryBackoffPolicy.class);
        private final IdempotencyExecutionGuard idempotencyExecutionGuard = Mockito.mock(IdempotencyExecutionGuard.class);
        private final NotificationRuleContextLoader ruleContextLoader = Mockito.mock(NotificationRuleContextLoader.class);
        private final NotificationRuleEngine ruleEngine = Mockito.mock(NotificationRuleEngine.class);
        private final NotificationRuleContext ruleContext = Mockito.mock(NotificationRuleContext.class);

        private final DefaultNotificationJobExecutor executor = new DefaultNotificationJobExecutor(
                channelRateLimiter,
                metricsRecorder,
                notificationJobRepository,
                notificationAttemptRepository,
                providerGateway,
                retryBackoffPolicy,
                idempotencyExecutionGuard,
                ruleContextLoader,
                ruleEngine);

        private Fixture() {
            when(ruleContextLoader.loadFor(job)).thenReturn(ruleContext);
            when(ruleEngine.evaluate(ruleContext, now)).thenReturn(NotificationRuleDecision.allow());
            when(channelRateLimiter.beforeDispatch(job.getChannel(), now))
                    .thenReturn(new BackPressureDecision(true, now, "ALLOWED"));
            when(idempotencyExecutionGuard.alreadyDelivered(job)).thenReturn(false);
        }

        private NotificationAttempt captureSavedAttempt() {
            ArgumentCaptor<NotificationAttempt> captor = ArgumentCaptor.forClass(NotificationAttempt.class);
            verify(notificationAttemptRepository).save(captor.capture());
            return captor.getValue();
        }
    }
}
