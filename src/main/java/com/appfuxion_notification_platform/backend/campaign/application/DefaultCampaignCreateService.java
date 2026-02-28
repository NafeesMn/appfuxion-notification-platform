package com.appfuxion_notification_platform.backend.campaign.application;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.appfuxion_notification_platform.backend.campaign.application.csv.CampaignCsvParser;
import com.appfuxion_notification_platform.backend.campaign.application.csv.CampaignCsvRow;
import com.appfuxion_notification_platform.backend.campaign.application.csv.CsvParseSummary;
import com.appfuxion_notification_platform.backend.campaign.domain.CampaignStatus;
import com.appfuxion_notification_platform.backend.campaign.persistence.Campaign;
import com.appfuxion_notification_platform.backend.campaign.persistence.CampaignImportRowError;
import com.appfuxion_notification_platform.backend.campaign.persistence.CampaignImportRowErrorRepository;
import com.appfuxion_notification_platform.backend.campaign.persistence.CampaignRecipient;
import com.appfuxion_notification_platform.backend.campaign.persistence.CampaignRecipientRepository;
import com.appfuxion_notification_platform.backend.campaign.persistence.CampaignRepository;
import com.appfuxion_notification_platform.backend.delivery.application.IdempotencyKeyFactory;
import com.appfuxion_notification_platform.backend.delivery.domain.NotificationJobStatus;
import com.appfuxion_notification_platform.backend.delivery.persistence.NotificationJob;
import com.appfuxion_notification_platform.backend.delivery.persistence.NotificationJobRepository;
import com.appfuxion_notification_platform.backend.outbox.domain.OutboxEventStatus;
import com.appfuxion_notification_platform.backend.outbox.persistence.OutboxEvent;
import com.appfuxion_notification_platform.backend.outbox.persistence.OutboxEventRepository;
import com.appfuxion_notification_platform.backend.tenant.persistence.Tenant;
import com.appfuxion_notification_platform.backend.tenant.persistence.TenantRepository;

/**
 * Phase 3 campaign creation orchestration.
 */
@Service
public class DefaultCampaignCreateService implements CampaignCreateService {

    private final TenantRepository tenantRepository;
    private final CampaignRepository campaignRepository;
    private final CampaignRecipientRepository campaignRecipientRepository;
    private final CampaignImportRowErrorRepository campaignImportRowErrorRepository;
    private final NotificationJobRepository notificationJobRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final CampaignCsvParser campaignCsvParser;
    private final RecipientRowValidator recipientRowValidator;
    private final IdempotencyKeyFactory idempotencyKeyFactory;
    private final Clock clock;

    public DefaultCampaignCreateService(
            TenantRepository tenantRepository,
            CampaignRepository campaignRepository,
            CampaignRecipientRepository campaignRecipientRepository,
            CampaignImportRowErrorRepository campaignImportRowErrorRepository,
            NotificationJobRepository notificationJobRepository,
            OutboxEventRepository outboxEventRepository,
            CampaignCsvParser campaignCsvParser,
            RecipientRowValidator recipientRowValidator,
            IdempotencyKeyFactory idempotencyKeyFactory,
            Clock clock) {
        this.tenantRepository = Objects.requireNonNull(tenantRepository);
        this.campaignRepository = Objects.requireNonNull(campaignRepository);
        this.campaignRecipientRepository = Objects.requireNonNull(campaignRecipientRepository);
        this.campaignImportRowErrorRepository = Objects.requireNonNull(campaignImportRowErrorRepository);
        this.notificationJobRepository = Objects.requireNonNull(notificationJobRepository);
        this.outboxEventRepository = Objects.requireNonNull(outboxEventRepository);
        this.campaignCsvParser = Objects.requireNonNull(campaignCsvParser);
        this.recipientRowValidator = Objects.requireNonNull(recipientRowValidator);
        this.idempotencyKeyFactory = Objects.requireNonNull(idempotencyKeyFactory);
        this.clock = Objects.requireNonNull(clock);
    }

    @Override
    @Transactional
    public CreateCampaignResult createCampaign(CreateCampaignCommand command) {
        Objects.requireNonNull(command, "command");

        String tenantKey = requireNonBlank(command.tenantKey(), "tenantKey");
        Objects.requireNonNull(command.correlationId(), "correlationId");
        Objects.requireNonNull(command.csvInputStream(), "csvInputStream");
        Objects.requireNonNull(command.channel(), "channel");
        Objects.requireNonNull(command.campaignType(), "campaignType");
        String messageTemplate = requireNonBlank(command.messageTemplate(), "messageTemplate");

        Tenant tenant = tenantRepository.findByTenantKey(tenantKey)
                        .orElseThrow(() -> new IllegalArgumentException("Unknown tenant key: " + tenantKey)); 

        String defaultTimezone = tenant.getDefaultTimezone();
        Instant acceptedAt = Instant.now(clock);

        Campaign campaign = buildIngestingCampaign(command, tenant, messageTemplate, acceptedAt);
        Campaign savedCampaign = campaignRepository.save(campaign);

        long[] acceptedRows = new long[] {0L};
        long[] invalidRows = new long[] {0L};

        CsvParseSummary parseSummary;
        try {
            parseSummary = campaignCsvParser.parse(
                    command.csvInputStream(),
                    command.channel(),
                    row -> processRow(
                            row,
                            command,
                            tenant,
                            defaultTimezone,
                            savedCampaign,
                            acceptedRows,
                            invalidRows));
        } catch (IOException ex) {
            throw new IllegalArgumentException("Failed to parse CSV input stream", ex);
        }

        long totalRows = parseSummary.totalRows();
        finalizeCampaign(savedCampaign, totalRows, acceptedRows[0], invalidRows[0], acceptedAt);

        boolean dispatchEnqueued = acceptedRows[0] > 0;
        if (dispatchEnqueued) {
            outboxEventRepository.save(buildDispatchRequestedOutbox(savedCampaign, acceptedRows[0], invalidRows[0], acceptedAt));
        }

        return new CreateCampaignResult(
                savedCampaign.getId(),
                savedCampaign.getCorrelationId(),
                savedCampaign.getStatus(),
                acceptedAt,
                totalRows,
                acceptedRows[0],
                invalidRows[0],
                dispatchEnqueued);
    }

    private Campaign buildIngestingCampaign(
            CreateCampaignCommand command,
            Tenant tenant,
            String messageTemplate,
            Instant acceptedAt) {
        Campaign campaign = new Campaign();
        campaign.setTenantId(tenant.getId());
        campaign.setCorrelationId(command.correlationId());
        campaign.setChannel(command.channel());
        campaign.setCampaignType(command.campaignType());
        campaign.setStatus(CampaignStatus.INGESTING);
        campaign.setMessageTemplate(messageTemplate);
        campaign.setNormalizedMessageHash(normalizedMessageHash(messageTemplate));
        campaign.setCreatedBy(command.createdBy());
        campaign.setImportStartedAt(acceptedAt);
        return campaign;
    }

    private void processRow(
            CampaignCsvRow row,
            CreateCampaignCommand command,
            Tenant tenant,
            String defaultTimezone,
            Campaign campaign,
            long[] acceptedRows,
            long[] invalidRows) {
        RecipientRowValidationResult validation = recipientRowValidator.validateAndNormalize(
                row,
                command.channel(),
                defaultTimezone);

        if (!validation.valid()) {
            invalidRows[0]++;
            campaignImportRowErrorRepository.save(buildImportRowError(campaign, tenant, row, validation));
            return;
        }

        CampaignRecipient recipient = campaignRecipientRepository.save(
                buildCampaignRecipient(command, campaign, tenant, row, validation));

        NotificationJob notificationJob = buildNotificationJob(command, campaign, tenant, recipient, validation);
        notificationJobRepository.save(notificationJob);
        acceptedRows[0]++;
    }

    private CampaignImportRowError buildImportRowError(
            Campaign campaign,
            Tenant tenant,
            CampaignCsvRow row,
            RecipientRowValidationResult validation) {
        CampaignImportRowError importRowError = new CampaignImportRowError();
        importRowError.setTenantId(tenant.getId());
        importRowError.setCampaignId(campaign.getId());
        importRowError.setRowNumber(row.rowNumber());
        importRowError.setErrorCode(validation.errorCode() != null ? validation.errorCode() : "INVALID_RECIPIENT_ROW");
        importRowError.setErrorMessage(validation.errorMessage());
        importRowError.setMaskedRowSnapshot(maskedRowSnapshot(row));
        return importRowError;
    }

    private CampaignRecipient buildCampaignRecipient(
            CreateCampaignCommand command,
            Campaign campaign,
            Tenant tenant,
            CampaignCsvRow row,
            RecipientRowValidationResult validation) {
        CampaignRecipient recipient = new CampaignRecipient();
        recipient.setTenantId(tenant.getId());
        recipient.setCampaignId(campaign.getId());
        recipient.setChannel(command.channel());
        recipient.setRowNumber(row.rowNumber());
        recipient.setNormalizedRecipientKey(validation.normalizedRecipientKey());
        recipient.setEmail(validation.email());
        recipient.setPhoneNumber(validation.phoneNumber());
        recipient.setDeviceToken(validation.deviceToken());
        recipient.setTimezone(validation.effectiveTimezone());
        recipient.setPersonalizationPayload(row.personalizationPayloadJson());
        recipient.setNormalizationStatus("ACCEPTED");
        return recipient;
    }

    private NotificationJob buildNotificationJob(
            CreateCampaignCommand command,
            Campaign campaign,
            Tenant tenant,
            CampaignRecipient recipient,
            RecipientRowValidationResult validation) {
        NotificationJob job = new NotificationJob();
        job.setTenantId(tenant.getId());
        job.setCampaignId(campaign.getId());
        job.setCampaignRecipientId(recipient.getId());
        job.setCorrelationId(campaign.getCorrelationId());
        job.setChannel(command.channel());
        job.setStatus(NotificationJobStatus.PENDING);
        job.setIdempotencyKey(idempotencyKeyFactory.build(
                tenant.getId(),
                campaign.getId(),
                recipient.getId(),
                command.channel(),
                campaign.getNormalizedMessageHash()));
        job.setPartitionKey(partitionKeyFor(validation.normalizedRecipientKey()));
        job.setAttemptCount(0);
        job.setMaxRetries(3);
        job.setNextAttemptAt(Instant.now(clock));
        return job;
    }

    private void finalizeCampaign(
            Campaign campaign,
            long totalRows,
            long acceptedRows,
            long invalidRows,
            Instant finalizedAt) {
        campaign.setRecipientCount(Math.toIntExact(acceptedRows));
        campaign.setInvalidRowCount(Math.toIntExact(invalidRows));
        campaign.setImportCompletedAt(finalizedAt);

        if (acceptedRows > 0) {
            campaign.setStatus(CampaignStatus.READY_FOR_DISPATCH);
            campaign.setDispatchRequestedAt(finalizedAt);
        } else {
            campaign.setStatus(CampaignStatus.FAILED_IMPORT);
        }

        if (totalRows > 0 && acceptedRows + invalidRows == 0) {
            campaign.setInvalidRowCount(Math.toIntExact(totalRows));
        }

        campaignRepository.save(campaign);
    }

    private OutboxEvent buildDispatchRequestedOutbox(
            Campaign campaign,
            long acceptedRows,
            long invalidRows,
            Instant availableAt) {
        OutboxEvent outboxEvent = new OutboxEvent();
        outboxEvent.setTenantId(campaign.getTenantId());
        outboxEvent.setAggregateType("Campaign");
        outboxEvent.setAggregateId(campaign.getId());
        outboxEvent.setEventType("CampaignDispatchRequested");
        outboxEvent.setPayload("""
                {"campaignId":"%s","correlationId":"%s","acceptedRows":%d,"invalidRows":%d}
                """.formatted(
                campaign.getId(),
                campaign.getCorrelationId(),
                acceptedRows,
                invalidRows).replace("\n", ""));
        outboxEvent.setStatus(OutboxEventStatus.NEW);
        outboxEvent.setAvailableAt(availableAt);
        outboxEvent.setRetryCount(0);
        return outboxEvent;
    }

    private int partitionKeyFor(String normalizedRecipientKey) {
        if (normalizedRecipientKey == null || normalizedRecipientKey.isBlank()) {
            return 0;
        }
        return Math.floorMod(normalizedRecipientKey.hashCode(), 128);
    }

    private String maskedRowSnapshot(CampaignCsvRow row) {
        String email = row.email() == null ? "" : maskEmail(row.email());
        String phone = row.phoneNumber() == null ? "" : maskPhone(row.phoneNumber());
        String device = row.deviceToken() == null ? "" : maskToken(row.deviceToken());
        String timezone = row.timezone() == null ? "" : row.timezone();
        return """
                {"rowNumber":%d,"email":"%s","phoneNumber":"%s","deviceToken":"%s","timezone":"%s"}
                """.formatted(row.rowNumber(), email, phone, device, timezone).replace("\n", "");
    }

    private String maskEmail(String email) {
        int atIndex = email.indexOf('@');
        if (atIndex <= 1) {
            return "***";
        }
        return email.charAt(0) + "***" + email.substring(atIndex);
    }

    private String maskPhone(String phone) {
        String digits = phone.replaceAll("\\D", "");
        if (digits.length() <= 4) {
            return "***";
        }
        return "***" + digits.substring(digits.length() - 4);
    }

    private String maskToken(String token) {
        if (token.length() <= 8) {
            return "***";
        }
        return token.substring(0, 4) + "***" + token.substring(token.length() - 4);
    }

    private String requireNonBlank(String value, String fieldName) {
        if(value == null || value.isBlank())
            throw new IllegalArgumentException(fieldName + " must not be blank");
        return value;
    }

    private String normalizedMessageHash(String messageTemplate) {
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(messageTemplate.strip().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
