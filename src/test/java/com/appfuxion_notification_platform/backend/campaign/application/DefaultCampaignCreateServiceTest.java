package com.appfuxion_notification_platform.backend.campaign.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import com.appfuxion_notification_platform.backend.campaign.application.csv.CampaignCsvParser;
import com.appfuxion_notification_platform.backend.campaign.application.csv.CampaignCsvRow;
import com.appfuxion_notification_platform.backend.campaign.application.csv.CsvParseSummary;
import com.appfuxion_notification_platform.backend.campaign.domain.CampaignStatus;
import com.appfuxion_notification_platform.backend.campaign.persistence.Campaign;
import com.appfuxion_notification_platform.backend.campaign.persistence.CampaignImportRowErrorRepository;
import com.appfuxion_notification_platform.backend.campaign.persistence.CampaignRecipient;
import com.appfuxion_notification_platform.backend.campaign.persistence.CampaignRecipientRepository;
import com.appfuxion_notification_platform.backend.campaign.persistence.CampaignRepository;
import com.appfuxion_notification_platform.backend.delivery.application.IdempotencyKeyFactory;
import com.appfuxion_notification_platform.backend.delivery.persistence.NotificationJobRepository;
import com.appfuxion_notification_platform.backend.domain.shared.CampaignType;
import com.appfuxion_notification_platform.backend.domain.shared.NotificationChannel;
import com.appfuxion_notification_platform.backend.outbox.persistence.OutboxEventRepository;
import com.appfuxion_notification_platform.backend.tenant.persistence.Tenant;
import com.appfuxion_notification_platform.backend.tenant.persistence.TenantRepository;

class DefaultCampaignCreateServiceTest {

    @Test
    void createCampaign_shouldPersistCampaignRecipientsJobsAndOutboxInOneTransactionalFlow() throws Exception {
        Fixture fixture = new Fixture();

        CampaignCsvRow validRow = new CampaignCsvRow(1, "alice@example.com", null, null, "UTC", null);
        CampaignCsvRow invalidRow = new CampaignCsvRow(2, "", null, null, "UTC", null);
        when(fixture.campaignCsvParser.parse(any(), eq(NotificationChannel.EMAIL), any()))
                .thenAnswer(invocation -> {
                    var rowConsumer = invocation.getArgument(2, com.appfuxion_notification_platform.backend.campaign.application.csv.CampaignCsvRowConsumer.class);
                    rowConsumer.accept(validRow);
                    rowConsumer.accept(invalidRow);
                    return new CsvParseSummary(2);
                });

        when(fixture.recipientRowValidator.validateAndNormalize(eq(validRow), eq(NotificationChannel.EMAIL), eq("UTC")))
                .thenReturn(new RecipientRowValidationResult(
                        true, "alice@example.com", "alice@example.com", null, null, "UTC", null, null));
        when(fixture.recipientRowValidator.validateAndNormalize(eq(invalidRow), eq(NotificationChannel.EMAIL), eq("UTC")))
                .thenReturn(new RecipientRowValidationResult(
                        false, null, null, null, null, "UTC", "INVALID_EMAIL", "email is required"));

        CreateCampaignResult result = fixture.service.createCampaign(fixture.command());

        assertEquals(2, result.totalRows());
        assertEquals(1, result.acceptedRows());
        assertEquals(1, result.invalidRows());
        assertTrue(result.dispatchEnqueued());
        assertEquals(CampaignStatus.READY_FOR_DISPATCH, result.status());

        verify(fixture.tenantRepository).findByTenantKey("tenant-acme");
        verify(fixture.campaignRecipientRepository).save(any(CampaignRecipient.class));
        verify(fixture.notificationJobRepository).save(any());
        verify(fixture.campaignImportRowErrorRepository).save(any());
        verify(fixture.outboxEventRepository).save(any());
    }

    @Test
    void createCampaign_shouldNotEnqueueOutboxEvent_whenAllRowsAreInvalid() throws Exception {
        Fixture fixture = new Fixture();

        CampaignCsvRow invalidRowOne = new CampaignCsvRow(1, "", null, null, "UTC", null);
        CampaignCsvRow invalidRowTwo = new CampaignCsvRow(2, "", null, null, "UTC", null);
        when(fixture.campaignCsvParser.parse(any(), eq(NotificationChannel.EMAIL), any()))
                .thenAnswer(invocation -> {
                    var rowConsumer = invocation.getArgument(2, com.appfuxion_notification_platform.backend.campaign.application.csv.CampaignCsvRowConsumer.class);
                    rowConsumer.accept(invalidRowOne);
                    rowConsumer.accept(invalidRowTwo);
                    return new CsvParseSummary(2);
                });

        when(fixture.recipientRowValidator.validateAndNormalize(eq(invalidRowOne), eq(NotificationChannel.EMAIL), eq("UTC")))
                .thenReturn(new RecipientRowValidationResult(
                        false, null, null, null, null, "UTC", "INVALID_ROW", "invalid"));
        when(fixture.recipientRowValidator.validateAndNormalize(eq(invalidRowTwo), eq(NotificationChannel.EMAIL), eq("UTC")))
                .thenReturn(new RecipientRowValidationResult(
                        false, null, null, null, null, "UTC", "INVALID_ROW", "invalid"));

        CreateCampaignResult result = fixture.service.createCampaign(fixture.command());

        assertEquals(2, result.totalRows());
        assertEquals(0, result.acceptedRows());
        assertEquals(2, result.invalidRows());
        assertFalse(result.dispatchEnqueued());
        assertEquals(CampaignStatus.FAILED_IMPORT, result.status());

        verify(fixture.campaignImportRowErrorRepository, times(2)).save(any());
        verify(fixture.campaignRecipientRepository, never()).save(any());
        verify(fixture.notificationJobRepository, never()).save(any());
        verify(fixture.outboxEventRepository, never()).save(any());
    }

    private static final class Fixture {
        private final TenantRepository tenantRepository = Mockito.mock(TenantRepository.class);
        private final CampaignRepository campaignRepository = Mockito.mock(CampaignRepository.class);
        private final CampaignRecipientRepository campaignRecipientRepository = Mockito.mock(CampaignRecipientRepository.class);
        private final CampaignImportRowErrorRepository campaignImportRowErrorRepository = Mockito.mock(CampaignImportRowErrorRepository.class);
        private final NotificationJobRepository notificationJobRepository = Mockito.mock(NotificationJobRepository.class);
        private final OutboxEventRepository outboxEventRepository = Mockito.mock(OutboxEventRepository.class);
        private final CampaignCsvParser campaignCsvParser = Mockito.mock(CampaignCsvParser.class);
        private final RecipientRowValidator recipientRowValidator = Mockito.mock(RecipientRowValidator.class);
        private final IdempotencyKeyFactory idempotencyKeyFactory = Mockito.mock(IdempotencyKeyFactory.class);
        private final Clock clock = Clock.fixed(Instant.parse("2026-03-01T00:00:00Z"), ZoneOffset.UTC);
        private final DefaultCampaignCreateService service = new DefaultCampaignCreateService(
                tenantRepository,
                campaignRepository,
                campaignRecipientRepository,
                campaignImportRowErrorRepository,
                notificationJobRepository,
                outboxEventRepository,
                campaignCsvParser,
                recipientRowValidator,
                idempotencyKeyFactory,
                clock);

        private final Tenant tenant = Mockito.mock(Tenant.class);
        private final UUID tenantId = UUID.randomUUID();
        private final UUID campaignId = UUID.randomUUID();
        private final UUID recipientId = UUID.randomUUID();
        private final UUID correlationId = UUID.randomUUID();

        private Fixture() {
            when(tenantRepository.findByTenantKey("tenant-acme")).thenReturn(Optional.of(tenant));
            when(tenant.getId()).thenReturn(tenantId);
            when(tenant.getDefaultTimezone()).thenReturn("UTC");

            when(campaignRepository.save(any(Campaign.class))).thenAnswer(invocation -> {
                Campaign campaign = invocation.getArgument(0);
                if (campaign.getId() == null) {
                    ReflectionTestUtils.setField(campaign, "id", campaignId);
                }
                return campaign;
            });

            when(campaignRecipientRepository.save(any(CampaignRecipient.class))).thenAnswer(invocation -> {
                CampaignRecipient recipient = invocation.getArgument(0);
                if (recipient.getId() == null) {
                    ReflectionTestUtils.setField(recipient, "id", recipientId);
                }
                return recipient;
            });

            when(idempotencyKeyFactory.build(any(), any(), any(), any(), any())).thenReturn("idem-key");
        }

        private CreateCampaignCommand command() {
            return new CreateCampaignCommand(
                    "tenant-acme",
                    correlationId,
                    NotificationChannel.EMAIL,
                    CampaignType.MARKETING,
                    "Hello {{name}}",
                    "tester",
                    "recipients.csv",
                    new ByteArrayInputStream("email\nalice@example.com".getBytes()));
        }
    }
}
