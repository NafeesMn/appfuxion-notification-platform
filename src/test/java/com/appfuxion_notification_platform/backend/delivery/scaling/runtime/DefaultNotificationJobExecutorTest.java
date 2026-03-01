package com.appfuxion_notification_platform.backend.delivery.scaling.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import com.appfuxion_notification_platform.backend.delivery.domain.NotificationJobStatus;
import com.appfuxion_notification_platform.backend.delivery.persistence.NotificationJob;
import com.appfuxion_notification_platform.backend.delivery.persistence.NotificationJobRepository;
import com.appfuxion_notification_platform.backend.delivery.rules.NotificationRuleContext;
import com.appfuxion_notification_platform.backend.delivery.rules.NotificationRuleContextLoader;
import com.appfuxion_notification_platform.backend.delivery.rules.NotificationRuleDecision;
import com.appfuxion_notification_platform.backend.delivery.rules.NotificationRuleEngine;
import com.appfuxion_notification_platform.backend.delivery.scaling.GlobalChannelRateLimiter;
import com.appfuxion_notification_platform.backend.delivery.scaling.ScalingMetricsRecorder;
import com.appfuxion_notification_platform.backend.delivery.scaling.domain.BackPressureDecision;
import com.appfuxion_notification_platform.backend.delivery.scaling.domain.WorkerIdentity;
import com.appfuxion_notification_platform.backend.domain.shared.NotificationChannel;

class DefaultNotificationJobExecutorTest {

    @Test
    void execute_shouldMarkSkippedWhenRuleEngineReturnsSkip() {
        GlobalChannelRateLimiter rateLimiter = Mockito.mock(GlobalChannelRateLimiter.class);
        ScalingMetricsRecorder metricsRecorder = Mockito.mock(ScalingMetricsRecorder.class);
        NotificationJobRepository notificationJobRepository = Mockito.mock(NotificationJobRepository.class);
        NotificationRuleContextLoader contextLoader = Mockito.mock(NotificationRuleContextLoader.class);
        NotificationRuleEngine ruleEngine = Mockito.mock(NotificationRuleEngine.class);

        NotificationJob job = pendingJob(NotificationChannel.SMS);
        NotificationRuleContext context = Mockito.mock(NotificationRuleContext.class);
        Instant now = Instant.parse("2026-03-01T12:00:00Z");

        when(contextLoader.loadFor(job)).thenReturn(context);
        when(ruleEngine.evaluate(context, now)).thenReturn(NotificationRuleDecision.skip("SUPPRESSED"));

        DefaultNotificationJobExecutor executor = new DefaultNotificationJobExecutor(
                rateLimiter, metricsRecorder, notificationJobRepository, contextLoader, ruleEngine);

        executor.execute(job, new WorkerIdentity("worker-a"), now);

        assertEquals(NotificationJobStatus.SKIPPED, job.getStatus());
        assertEquals("SUPPRESSED", job.getLastRuleReasonCode());
        assertEquals(now, job.getCompletedAt());
        verify(notificationJobRepository).save(job);
        verify(rateLimiter, never()).beforeDispatch(any(), any());
    }

    @Test
    void execute_shouldMarkDelayedWhenQuietHoursRuleDelays() {
        GlobalChannelRateLimiter rateLimiter = Mockito.mock(GlobalChannelRateLimiter.class);
        ScalingMetricsRecorder metricsRecorder = Mockito.mock(ScalingMetricsRecorder.class);
        NotificationJobRepository notificationJobRepository = Mockito.mock(NotificationJobRepository.class);
        NotificationRuleContextLoader contextLoader = Mockito.mock(NotificationRuleContextLoader.class);
        NotificationRuleEngine ruleEngine = Mockito.mock(NotificationRuleEngine.class);

        NotificationJob job = pendingJob(NotificationChannel.PUSH);
        NotificationRuleContext context = Mockito.mock(NotificationRuleContext.class);
        Instant now = Instant.parse("2026-03-01T12:00:00Z");
        Instant nextEligibleAt = Instant.parse("2026-03-01T16:00:00Z");

        when(contextLoader.loadFor(job)).thenReturn(context);
        when(ruleEngine.evaluate(context, now)).thenReturn(NotificationRuleDecision.delay("QUIET_HOURS_ACTIVE", nextEligibleAt));

        DefaultNotificationJobExecutor executor = new DefaultNotificationJobExecutor(
                rateLimiter, metricsRecorder, notificationJobRepository, contextLoader, ruleEngine);

        executor.execute(job, new WorkerIdentity("worker-b"), now);

        assertEquals(NotificationJobStatus.DELAYED, job.getStatus());
        assertEquals(nextEligibleAt, job.getDeferredUntil());
        assertEquals(nextEligibleAt, job.getNextAttemptAt());
        assertEquals("QUIET_HOURS_ACTIVE", job.getLastRuleReasonCode());
        verify(notificationJobRepository).save(job);
        verify(rateLimiter, never()).beforeDispatch(any(), any());
    }

    @Test
    void execute_shouldApplyRateLimiterWhenRulesAllow() {
        GlobalChannelRateLimiter rateLimiter = Mockito.mock(GlobalChannelRateLimiter.class);
        ScalingMetricsRecorder metricsRecorder = Mockito.mock(ScalingMetricsRecorder.class);
        NotificationJobRepository notificationJobRepository = Mockito.mock(NotificationJobRepository.class);
        NotificationRuleContextLoader contextLoader = Mockito.mock(NotificationRuleContextLoader.class);
        NotificationRuleEngine ruleEngine = Mockito.mock(NotificationRuleEngine.class);

        NotificationJob job = pendingJob(NotificationChannel.SMS);
        NotificationRuleContext context = Mockito.mock(NotificationRuleContext.class);
        Instant now = Instant.parse("2026-03-01T12:00:00Z");
        Instant nextEligibleAt = Instant.parse("2026-03-01T12:01:00Z");

        when(contextLoader.loadFor(job)).thenReturn(context);
        when(ruleEngine.evaluate(context, now)).thenReturn(NotificationRuleDecision.allow());
        when(rateLimiter.beforeDispatch(NotificationChannel.SMS, now))
                .thenReturn(new BackPressureDecision(false, nextEligibleAt, "CHANNEL_RATE_LIMIT_EXCEEDED"));

        DefaultNotificationJobExecutor executor = new DefaultNotificationJobExecutor(
                rateLimiter, metricsRecorder, notificationJobRepository, contextLoader, ruleEngine);

        executor.execute(job, new WorkerIdentity("worker-c"), now);

        assertEquals(NotificationJobStatus.DELAYED, job.getStatus());
        assertEquals(nextEligibleAt, job.getNextAttemptAt());
        assertEquals("CHANNEL_RATE_LIMIT_EXCEEDED", job.getLastRuleReasonCode());
        verify(metricsRecorder).recordThrottleDeferral(NotificationChannel.SMS);
        verify(notificationJobRepository).save(job);
    }

    private NotificationJob pendingJob(NotificationChannel channel) {
        NotificationJob job = new NotificationJob();
        job.setTenantId(UUID.randomUUID());
        job.setCampaignId(UUID.randomUUID());
        job.setCampaignRecipientId(UUID.randomUUID());
        job.setChannel(channel);
        job.setStatus(NotificationJobStatus.PENDING);
        return job;
    }
}
