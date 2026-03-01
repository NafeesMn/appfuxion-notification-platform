package com.appfuxion_notification_platform.backend.campaign.application;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.appfuxion_notification_platform.backend.api.campaign.dto.RetryFailuresAcceptedResponse;
import com.appfuxion_notification_platform.backend.campaign.domain.CampaignStatus;
import com.appfuxion_notification_platform.backend.campaign.persistence.Campaign;
import com.appfuxion_notification_platform.backend.campaign.persistence.CampaignRepository;
import com.appfuxion_notification_platform.backend.delivery.domain.NotificationJobStatus;
import com.appfuxion_notification_platform.backend.delivery.persistence.NotificationJob;
import com.appfuxion_notification_platform.backend.delivery.persistence.NotificationJobRepository;
import com.appfuxion_notification_platform.backend.outbox.domain.OutboxEventStatus;
import com.appfuxion_notification_platform.backend.outbox.persistence.OutboxEvent;
import com.appfuxion_notification_platform.backend.outbox.persistence.OutboxEventRepository;
import com.appfuxion_notification_platform.backend.tenant.persistence.Tenant;
import com.appfuxion_notification_platform.backend.tenant.persistence.TenantRepository;

@Service
@Transactional
public class DefaultCampaignRetryFailuresService implements CampaignRetryFailuresService {

    private static final String RETRY_STATUS = "RETRY_ACCEPTED";

    private final TenantRepository tenantRepository;
    private final CampaignRepository campaignRepository;
    private final NotificationJobRepository notificationJobRepository;
    private final OutboxEventRepository outboxEventRepository;

    public DefaultCampaignRetryFailuresService(
            TenantRepository tenantRepository,
            CampaignRepository campaignRepository,
            NotificationJobRepository notificationJobRepository,
            OutboxEventRepository outboxEventRepository) {
        this.tenantRepository = Objects.requireNonNull(tenantRepository);
        this.campaignRepository = Objects.requireNonNull(campaignRepository);
        this.notificationJobRepository = Objects.requireNonNull(notificationJobRepository);
        this.outboxEventRepository = Objects.requireNonNull(outboxEventRepository);
    }

    @Override
    public RetryFailuresAcceptedResponse retryFailures(String tenantKey, UUID campaignId) {
        Objects.requireNonNull(campaignId, "campaignId");
        Tenant tenant = requireTenant(tenantKey);
        Campaign campaign = campaignRepository.findByIdAndTenantId(campaignId, tenant.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Campaign not found"));

        Instant requestedAt = Instant.now();
        List<NotificationJob> failedJobs = notificationJobRepository.findByCampaignIdAndTenantIdAndStatus(
                campaignId,
                tenant.getId(),
                NotificationJobStatus.FAILED);

        int requeuedCount = requeueFailedJobs(failedJobs, requestedAt);
        if (requeuedCount > 0) {
            campaign.setStatus(CampaignStatus.PROCESSING);
            campaignRepository.save(campaign);
            outboxEventRepository.save(buildRetryRequestedOutbox(campaign, requestedAt, requeuedCount));
        }

        return new RetryFailuresAcceptedResponse(campaignId, RETRY_STATUS, requestedAt);
    }

    private int requeueFailedJobs(List<NotificationJob> failedJobs, Instant requestedAt) {
        if (failedJobs.isEmpty()) {
            return 0;
        }

        for (NotificationJob failedJob : failedJobs) {
            failedJob.setStatus(NotificationJobStatus.RETRY_SCHEDULED);
            failedJob.setAttemptCount(0);
            failedJob.setNextAttemptAt(requestedAt);
            failedJob.setDeferredUntil(null);
            failedJob.setCompletedAt(null);
            failedJob.setLastErrorCode(null);
            failedJob.setLastErrorMessage(null);
            failedJob.setLastRuleReasonCode("MANUAL_RETRY_REQUESTED");
        }
        notificationJobRepository.saveAll(failedJobs);
        return failedJobs.size();
    }

    private OutboxEvent buildRetryRequestedOutbox(Campaign campaign, Instant requestedAt, int requeuedCount) {
        OutboxEvent outboxEvent = new OutboxEvent();
        outboxEvent.setTenantId(campaign.getTenantId());
        outboxEvent.setAggregateType("Campaign");
        outboxEvent.setAggregateId(campaign.getId());
        outboxEvent.setEventType("CampaignRetryFailuresRequested");
        outboxEvent.setPayload("""
                {"campaignId":"%s","correlationId":"%s","requestedAt":"%s","requeuedCount":%d}
                """.formatted(
                campaign.getId(),
                campaign.getCorrelationId(),
                requestedAt,
                requeuedCount).replace("\n", ""));
        outboxEvent.setStatus(OutboxEventStatus.NEW);
        outboxEvent.setAvailableAt(requestedAt);
        outboxEvent.setRetryCount(0);
        return outboxEvent;
    }

    private Tenant requireTenant(String tenantKey) {
        if (tenantKey == null || tenantKey.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tenant key must not be blank");
        }

        return tenantRepository.findByTenantKey(tenantKey)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Tenant not found"));
    }
}
