package com.appfuxion_notification_platform.backend.campaign.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import com.appfuxion_notification_platform.backend.api.campaign.dto.RetryFailuresAcceptedResponse;
import com.appfuxion_notification_platform.backend.campaign.domain.CampaignStatus;
import com.appfuxion_notification_platform.backend.campaign.persistence.Campaign;
import com.appfuxion_notification_platform.backend.delivery.persistence.NotificationAttempt;
import com.appfuxion_notification_platform.backend.delivery.persistence.NotificationAttemptRepository;
import com.appfuxion_notification_platform.backend.campaign.persistence.CampaignRepository;
import com.appfuxion_notification_platform.backend.delivery.domain.NotificationJobStatus;
import com.appfuxion_notification_platform.backend.delivery.persistence.NotificationJob;
import com.appfuxion_notification_platform.backend.delivery.persistence.NotificationJobRepository;
import com.appfuxion_notification_platform.backend.outbox.persistence.OutboxEvent;
import com.appfuxion_notification_platform.backend.outbox.persistence.OutboxEventRepository;
import com.appfuxion_notification_platform.backend.persistence.common.BaseEntity;
import com.appfuxion_notification_platform.backend.tenant.persistence.Tenant;
import com.appfuxion_notification_platform.backend.tenant.persistence.TenantRepository;

class DefaultCampaignRetryFailuresServiceTest {

    @Test
    void retryFailures_shouldRequeueFailedJobsAndPublishOutboxEvent() {
        TenantRepository tenantRepository = Mockito.mock(TenantRepository.class);
        CampaignRepository campaignRepository = Mockito.mock(CampaignRepository.class);
        NotificationJobRepository notificationJobRepository = Mockito.mock(NotificationJobRepository.class);
        NotificationAttemptRepository notificationAttemptRepository = Mockito.mock(NotificationAttemptRepository.class);
        OutboxEventRepository outboxEventRepository = Mockito.mock(OutboxEventRepository.class);

        DefaultCampaignRetryFailuresService service = new DefaultCampaignRetryFailuresService(
                tenantRepository,
                campaignRepository,
                notificationJobRepository,
                notificationAttemptRepository,
                outboxEventRepository);

        UUID tenantId = UUID.randomUUID();
        UUID campaignId = UUID.randomUUID();
        Tenant tenant = Mockito.mock(Tenant.class);
        Campaign campaign = Mockito.mock(Campaign.class);
        NotificationJob failedJob = new NotificationJob();
        UUID notificationJobId = UUID.randomUUID();
        setEntityId(failedJob, notificationJobId);
        failedJob.setStatus(NotificationJobStatus.FAILED);
        failedJob.setAttemptCount(5);
        failedJob.setMaxRetries(3);

        NotificationAttempt lastAttempt = new NotificationAttempt();
        lastAttempt.setAttemptNumber(5);

        when(tenant.getId()).thenReturn(tenantId);
        when(tenantRepository.findByTenantKey("tenant-acme")).thenReturn(Optional.of(tenant));
        when(campaignRepository.findByIdAndTenantId(campaignId, tenantId)).thenReturn(Optional.of(campaign));
        when(campaign.getId()).thenReturn(campaignId);
        when(campaign.getTenantId()).thenReturn(tenantId);
        when(campaign.getCorrelationId()).thenReturn(UUID.randomUUID());
        when(notificationJobRepository.findByCampaignIdAndTenantIdAndStatus(
                campaignId,
                tenantId,
                NotificationJobStatus.FAILED)).thenReturn(List.of(failedJob));
        when(notificationAttemptRepository.findTopByNotificationJobIdOrderByAttemptNumberDesc(notificationJobId))
                .thenReturn(Optional.of(lastAttempt));

        RetryFailuresAcceptedResponse response = service.retryFailures("tenant-acme", campaignId);

        assertEquals(campaignId, response.campaignId());
        assertEquals("RETRY_ACCEPTED", response.status());
        assertEquals(NotificationJobStatus.RETRY_SCHEDULED, failedJob.getStatus());
        assertEquals(5, failedJob.getAttemptCount());
        assertEquals(8, failedJob.getMaxRetries());
        assertEquals("MANUAL_RETRY_REQUESTED", failedJob.getLastRuleReasonCode());
        verify(notificationJobRepository).saveAll(any());
        verify(campaign).setStatus(CampaignStatus.PROCESSING);
        verify(campaignRepository).save(campaign);
        verify(outboxEventRepository).save(any(OutboxEvent.class));
    }

    @Test
    void retryFailures_shouldNotPublishOutboxWhenNoFailedJobs() {
        TenantRepository tenantRepository = Mockito.mock(TenantRepository.class);
        CampaignRepository campaignRepository = Mockito.mock(CampaignRepository.class);
        NotificationJobRepository notificationJobRepository = Mockito.mock(NotificationJobRepository.class);
        NotificationAttemptRepository notificationAttemptRepository = Mockito.mock(NotificationAttemptRepository.class);
        OutboxEventRepository outboxEventRepository = Mockito.mock(OutboxEventRepository.class);

        DefaultCampaignRetryFailuresService service = new DefaultCampaignRetryFailuresService(
                tenantRepository,
                campaignRepository,
                notificationJobRepository,
                notificationAttemptRepository,
                outboxEventRepository);

        UUID tenantId = UUID.randomUUID();
        UUID campaignId = UUID.randomUUID();
        Tenant tenant = Mockito.mock(Tenant.class);
        Campaign campaign = Mockito.mock(Campaign.class);

        when(tenant.getId()).thenReturn(tenantId);
        when(tenantRepository.findByTenantKey("tenant-acme")).thenReturn(Optional.of(tenant));
        when(campaignRepository.findByIdAndTenantId(campaignId, tenantId)).thenReturn(Optional.of(campaign));
        when(notificationJobRepository.findByCampaignIdAndTenantIdAndStatus(
                campaignId,
                tenantId,
                NotificationJobStatus.FAILED)).thenReturn(List.of());

        service.retryFailures("tenant-acme", campaignId);

        verify(notificationJobRepository, never()).saveAll(any());
        verify(campaignRepository, never()).save(any(Campaign.class));
        verify(outboxEventRepository, never()).save(any(OutboxEvent.class));
    }

    @Test
    void retryFailures_shouldReturn404WhenCampaignMissing() {
        TenantRepository tenantRepository = Mockito.mock(TenantRepository.class);
        CampaignRepository campaignRepository = Mockito.mock(CampaignRepository.class);
        NotificationJobRepository notificationJobRepository = Mockito.mock(NotificationJobRepository.class);
        NotificationAttemptRepository notificationAttemptRepository = Mockito.mock(NotificationAttemptRepository.class);
        OutboxEventRepository outboxEventRepository = Mockito.mock(OutboxEventRepository.class);

        DefaultCampaignRetryFailuresService service = new DefaultCampaignRetryFailuresService(
                tenantRepository,
                campaignRepository,
                notificationJobRepository,
                notificationAttemptRepository,
                outboxEventRepository);

        UUID tenantId = UUID.randomUUID();
        Tenant tenant = Mockito.mock(Tenant.class);
        when(tenant.getId()).thenReturn(tenantId);
        when(tenantRepository.findByTenantKey("tenant-acme")).thenReturn(Optional.of(tenant));
        when(campaignRepository.findByIdAndTenantId(any(), any())).thenReturn(Optional.empty());

        ResponseStatusException ex = org.junit.jupiter.api.Assertions.assertThrows(
                ResponseStatusException.class,
                () -> service.retryFailures("tenant-acme", UUID.randomUUID()));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
    }

    private static void setEntityId(BaseEntity entity, UUID id) {
        try {
            Field idField = BaseEntity.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(entity, id);
        } catch (ReflectiveOperationException ex) {
            throw new AssertionError("Failed to set entity id for test fixture", ex);
        }
    }
}
