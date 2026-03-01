package com.appfuxion_notification_platform.backend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;

import com.appfuxion_notification_platform.backend.campaign.application.CampaignCreateService;
import com.appfuxion_notification_platform.backend.campaign.application.CampaignQueryService;
import com.appfuxion_notification_platform.backend.campaign.application.CampaignRetryFailuresService;
import com.appfuxion_notification_platform.backend.campaign.application.CreateCampaignCommand;
import com.appfuxion_notification_platform.backend.campaign.application.CreateCampaignResult;
import com.appfuxion_notification_platform.backend.campaign.domain.CampaignStatus;
import com.appfuxion_notification_platform.backend.campaign.persistence.Campaign;
import com.appfuxion_notification_platform.backend.campaign.persistence.CampaignImportRowErrorRepository;
import com.appfuxion_notification_platform.backend.campaign.persistence.CampaignRecipientRepository;
import com.appfuxion_notification_platform.backend.campaign.persistence.CampaignRepository;
import com.appfuxion_notification_platform.backend.delivery.domain.NotificationJobStatus;
import com.appfuxion_notification_platform.backend.delivery.persistence.NotificationJob;
import com.appfuxion_notification_platform.backend.delivery.persistence.NotificationJobRepository;
import com.appfuxion_notification_platform.backend.domain.shared.CampaignType;
import com.appfuxion_notification_platform.backend.domain.shared.NotificationChannel;
import com.appfuxion_notification_platform.backend.outbox.persistence.OutboxEventRepository;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@EnabledIfEnvironmentVariable(named = "RUN_DOCKER_TESTS", matches = "true")
class CampaignFlowsIntegrationTest {

    @Autowired
    private CampaignCreateService campaignCreateService;

    @Autowired
    private CampaignQueryService campaignQueryService;

    @Autowired
    private CampaignRetryFailuresService campaignRetryFailuresService;

    @Autowired
    private CampaignRepository campaignRepository;

    @Autowired
    private CampaignRecipientRepository campaignRecipientRepository;

    @Autowired
    private CampaignImportRowErrorRepository campaignImportRowErrorRepository;

    @Autowired
    private NotificationJobRepository notificationJobRepository;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanDatabase() {
        jdbcTemplate.execute("""
                TRUNCATE TABLE
                    notification_attempts,
                    notification_jobs,
                    campaign_import_row_errors,
                    campaign_recipients,
                    outbox_events,
                    campaigns,
                    global_suppression_entries,
                    tenants,
                    worker_partition_leases
                RESTART IDENTITY CASCADE
                """);
    }

    @Test
    void createCampaign_shouldPersistRowsOutboxAndEnforceTenantIsolationOnQuery() {
        UUID tenantAId = insertTenant("tenant-a", "UTC");
        insertTenant("tenant-b", "Asia/Kuala_Lumpur");

        CreateCampaignResult result = campaignCreateService.createCampaign(createEmailCampaignCommand(
                "tenant-a",
                """
                        email,timezone
                        alice@example.com,America/New_York
                        invalid-email,America/New_York
                        bob@example.com,
                        """));

        assertEquals(CampaignStatus.READY_FOR_DISPATCH, result.status());
        assertTrue(result.dispatchEnqueued());
        assertEquals(3, result.totalRows());
        assertEquals(2, result.acceptedRows());
        assertEquals(1, result.invalidRows());

        UUID campaignId = result.campaignId();
        assertEquals(2, campaignRecipientRepository.countByCampaignIdAndTenantId(campaignId, tenantAId));
        assertEquals(1, campaignImportRowErrorRepository.countByCampaignIdAndTenantId(campaignId, tenantAId));
        assertEquals(2, notificationJobRepository.countByCampaignIdAndTenantId(campaignId, tenantAId));
        assertEquals(
                1,
                outboxEventRepository.findAll().stream()
                        .filter(event -> event.getAggregateId().equals(campaignId))
                        .filter(event -> "CampaignDispatchRequested".equals(event.getEventType()))
                        .count());

        var detail = campaignQueryService.getCampaign("tenant-a", campaignId);
        assertEquals(3, detail.importSummary().totalRows());
        assertEquals(2, detail.importSummary().acceptedRows());
        assertEquals(1, detail.importSummary().invalidRows());
        assertEquals(2, detail.deliverySummary().total());

        ResponseStatusException isolationError = assertThrows(
                ResponseStatusException.class,
                () -> campaignQueryService.getCampaign("tenant-b", campaignId));
        assertEquals(HttpStatus.NOT_FOUND, isolationError.getStatusCode());
        assertTrue(campaignQueryService.listCampaigns("tenant-b", 0, 20, null, null).items().isEmpty());
    }

    @Test
    void retryFailures_shouldRequeueOnlyFailedJobsAndEmitRetryOutboxEvent() {
        UUID tenantId = insertTenant("tenant-a", "UTC");

        CreateCampaignResult result = campaignCreateService.createCampaign(createEmailCampaignCommand(
                "tenant-a",
                """
                        email,timezone
                        alice@example.com,UTC
                        bob@example.com,UTC
                        """));
        UUID campaignId = result.campaignId();

        List<NotificationJob> pendingJobs = notificationJobRepository.findByCampaignIdAndTenantIdAndStatus(
                campaignId,
                tenantId,
                NotificationJobStatus.PENDING);
        assertEquals(2, pendingJobs.size());

        NotificationJob failedJob = pendingJobs.get(0);
        failedJob.setStatus(NotificationJobStatus.FAILED);
        failedJob.setAttemptCount(2);
        failedJob.setCompletedAt(Instant.now());
        failedJob.setLastErrorCode("PROVIDER_TEMP");
        failedJob.setLastErrorMessage("temporary");

        NotificationJob sentJob = pendingJobs.get(1);
        sentJob.setStatus(NotificationJobStatus.SENT);
        sentJob.setAttemptCount(1);
        sentJob.setCompletedAt(Instant.now());

        notificationJobRepository.saveAll(List.of(failedJob, sentJob));

        campaignRetryFailuresService.retryFailures("tenant-a", campaignId);

        List<NotificationJob> requeuedJobs = notificationJobRepository.findByCampaignIdAndTenantIdAndStatus(
                campaignId,
                tenantId,
                NotificationJobStatus.RETRY_SCHEDULED);
        assertEquals(1, requeuedJobs.size());
        NotificationJob requeuedJob = requeuedJobs.get(0);
        assertEquals(failedJob.getId(), requeuedJob.getId());
        assertEquals(0, requeuedJob.getAttemptCount());
        assertEquals("MANUAL_RETRY_REQUESTED", requeuedJob.getLastRuleReasonCode());
        assertNotNull(requeuedJob.getNextAttemptAt());
        assertNull(requeuedJob.getCompletedAt());

        List<NotificationJob> stillSentJobs = notificationJobRepository.findByCampaignIdAndTenantIdAndStatus(
                campaignId,
                tenantId,
                NotificationJobStatus.SENT);
        assertEquals(1, stillSentJobs.size());
        assertEquals(sentJob.getId(), stillSentJobs.get(0).getId());

        Campaign updatedCampaign = campaignRepository.findById(campaignId).orElseThrow();
        assertEquals(CampaignStatus.PROCESSING, updatedCampaign.getStatus());
        assertEquals(
                1,
                outboxEventRepository.findAll().stream()
                        .filter(event -> event.getAggregateId().equals(campaignId))
                        .filter(event -> "CampaignRetryFailuresRequested".equals(event.getEventType()))
                        .count());
    }

    @Test
    void retryFailures_shouldRejectCrossTenantAccessAndLeaveFailedJobsUntouched() {
        UUID tenantAId = insertTenant("tenant-a", "UTC");
        insertTenant("tenant-b", "UTC");

        CreateCampaignResult result = campaignCreateService.createCampaign(createEmailCampaignCommand(
                "tenant-a",
                """
                        email,timezone
                        alice@example.com,UTC
                        """));
        UUID campaignId = result.campaignId();

        NotificationJob failedJob = notificationJobRepository.findByCampaignIdAndTenantIdAndStatus(
                campaignId,
                tenantAId,
                NotificationJobStatus.PENDING).getFirst();
        failedJob.setStatus(NotificationJobStatus.FAILED);
        failedJob.setAttemptCount(1);
        notificationJobRepository.save(failedJob);

        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> campaignRetryFailuresService.retryFailures("tenant-b", campaignId));
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());

        NotificationJob untouched = notificationJobRepository.findById(failedJob.getId()).orElseThrow();
        assertEquals(NotificationJobStatus.FAILED, untouched.getStatus());
        assertNull(untouched.getLastRuleReasonCode());
    }

    private UUID insertTenant(String tenantKey, String defaultTimezone) {
        UUID tenantId = UUID.randomUUID();
        jdbcTemplate.update(
                """
                        INSERT INTO tenants (id, tenant_key, name, default_timezone, status, created_at, updated_at)
                        VALUES (?, ?, ?, ?, ?, NOW(), NOW())
                        """,
                tenantId,
                tenantKey,
                "Tenant " + tenantKey,
                defaultTimezone,
                "ACTIVE");
        return tenantId;
    }

    private CreateCampaignCommand createEmailCampaignCommand(String tenantKey, String csvContent) {
        return new CreateCampaignCommand(
                tenantKey,
                UUID.randomUUID(),
                NotificationChannel.EMAIL,
                CampaignType.MARKETING,
                "Hello {{name}}",
                "phase9-integration-test",
                "recipients.csv",
                new ByteArrayInputStream(csvContent.getBytes(StandardCharsets.UTF_8)));
    }

}
