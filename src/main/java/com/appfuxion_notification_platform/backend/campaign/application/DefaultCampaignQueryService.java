package com.appfuxion_notification_platform.backend.campaign.application;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.appfuxion_notification_platform.backend.api.campaign.dto.CampaignDeliverySummaryResponse;
import com.appfuxion_notification_platform.backend.api.campaign.dto.CampaignDetailResponse;
import com.appfuxion_notification_platform.backend.api.campaign.dto.CampaignImportSummaryResponse;
import com.appfuxion_notification_platform.backend.api.campaign.dto.CampaignListResponse;
import com.appfuxion_notification_platform.backend.api.campaign.dto.CampaignSummaryResponse;
import com.appfuxion_notification_platform.backend.campaign.domain.CampaignStatus;
import com.appfuxion_notification_platform.backend.campaign.persistence.Campaign;
import com.appfuxion_notification_platform.backend.campaign.persistence.CampaignImportRowErrorRepository;
import com.appfuxion_notification_platform.backend.campaign.persistence.CampaignRecipientRepository;
import com.appfuxion_notification_platform.backend.campaign.persistence.CampaignRepository;
import com.appfuxion_notification_platform.backend.delivery.domain.NotificationJobStatus;
import com.appfuxion_notification_platform.backend.delivery.persistence.NotificationJobRepository;
import com.appfuxion_notification_platform.backend.domain.shared.NotificationChannel;
import com.appfuxion_notification_platform.backend.tenant.persistence.Tenant;
import com.appfuxion_notification_platform.backend.tenant.persistence.TenantRepository;

@Service
@Transactional(readOnly = true)
public class DefaultCampaignQueryService implements CampaignQueryService {

    private static final Set<NotificationJobStatus> PENDING_STATUSES = Set.of(
            NotificationJobStatus.PENDING,
            NotificationJobStatus.PROCESSING,
            NotificationJobStatus.RETRY_SCHEDULED);

    private final TenantRepository tenantRepository;
    private final CampaignRepository campaignRepository;
    private final CampaignRecipientRepository campaignRecipientRepository;
    private final CampaignImportRowErrorRepository campaignImportRowErrorRepository;
    private final NotificationJobRepository notificationJobRepository;

    public DefaultCampaignQueryService(
            TenantRepository tenantRepository,
            CampaignRepository campaignRepository,
            CampaignRecipientRepository campaignRecipientRepository,
            CampaignImportRowErrorRepository campaignImportRowErrorRepository,
            NotificationJobRepository notificationJobRepository) {
        this.tenantRepository = Objects.requireNonNull(tenantRepository);
        this.campaignRepository = Objects.requireNonNull(campaignRepository);
        this.campaignRecipientRepository = Objects.requireNonNull(campaignRecipientRepository);
        this.campaignImportRowErrorRepository = Objects.requireNonNull(campaignImportRowErrorRepository);
        this.notificationJobRepository = Objects.requireNonNull(notificationJobRepository);
    }

    @Override
    public CampaignListResponse listCampaigns(
            String tenantKey,
            int page,
            int size,
            CampaignStatus status,
            NotificationChannel channel) {
        Tenant tenant = requireTenant(tenantKey);
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));

        Page<Campaign> campaigns = findByFilters(tenant.getId(), status, channel, pageable);
        List<CampaignSummaryResponse> items = campaigns.getContent().stream()
                .map(campaign -> toSummary(campaign, tenant.getId()))
                .toList();

        return new CampaignListResponse(
                page,
                size,
                campaigns.getTotalElements(),
                campaigns.getTotalPages(),
                items);
    }

    @Override
    public CampaignDetailResponse getCampaign(String tenantKey, UUID campaignId) {
        Objects.requireNonNull(campaignId, "campaignId");
        Tenant tenant = requireTenant(tenantKey);
        Campaign campaign = campaignRepository.findByIdAndTenantId(campaignId, tenant.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Campaign not found"));

        return toDetail(campaign, tenant.getId());
    }

    private Page<Campaign> findByFilters(
            UUID tenantId,
            CampaignStatus status,
            NotificationChannel channel,
            Pageable pageable) {
        if (status != null && channel != null) {
            return campaignRepository.findByTenantIdAndStatusAndChannel(tenantId, status, channel, pageable);
        }
        if (status != null) {
            return campaignRepository.findByTenantIdAndStatus(tenantId, status, pageable);
        }
        if (channel != null) {
            return campaignRepository.findByTenantIdAndChannel(tenantId, channel, pageable);
        }
        return campaignRepository.findByTenantId(tenantId, pageable);
    }

    private CampaignSummaryResponse toSummary(Campaign campaign, UUID tenantId) {
        return new CampaignSummaryResponse(
                campaign.getId(),
                campaign.getChannel(),
                campaign.getCampaignType(),
                campaign.getStatus(),
                campaign.getCreatedAt(),
                deliverySummary(campaign.getId(), tenantId));
    }

    private CampaignDetailResponse toDetail(Campaign campaign, UUID tenantId) {
        long acceptedRows = campaignRecipientRepository.countByCampaignIdAndTenantId(campaign.getId(), tenantId);
        long invalidRows = campaignImportRowErrorRepository.countByCampaignIdAndTenantId(campaign.getId(), tenantId);
        CampaignImportSummaryResponse importSummary = new CampaignImportSummaryResponse(
                acceptedRows + invalidRows,
                acceptedRows,
                invalidRows);

        return new CampaignDetailResponse(
                campaign.getId(),
                campaign.getCorrelationId(),
                campaign.getChannel(),
                campaign.getCampaignType(),
                campaign.getStatus(),
                campaign.getMessageTemplate(),
                campaign.getCreatedBy(),
                campaign.getCreatedAt(),
                campaign.getUpdatedAt(),
                importSummary,
                deliverySummary(campaign.getId(), tenantId));
    }

    private CampaignDeliverySummaryResponse deliverySummary(UUID campaignId, UUID tenantId) {
        long total = notificationJobRepository.countByCampaignIdAndTenantId(campaignId, tenantId);
        long sent = notificationJobRepository.countByCampaignIdAndTenantIdAndStatus(campaignId, tenantId, NotificationJobStatus.SENT);
        long failed = notificationJobRepository.countByCampaignIdAndTenantIdAndStatus(campaignId, tenantId, NotificationJobStatus.FAILED);
        long skipped = notificationJobRepository.countByCampaignIdAndTenantIdAndStatus(campaignId, tenantId, NotificationJobStatus.SKIPPED);
        long delayed = notificationJobRepository.countByCampaignIdAndTenantIdAndStatus(campaignId, tenantId, NotificationJobStatus.DELAYED);
        long pending = notificationJobRepository.countByCampaignIdAndTenantIdAndStatusIn(campaignId, tenantId, PENDING_STATUSES);

        return new CampaignDeliverySummaryResponse(total, sent, failed, skipped, delayed, pending);
    }

    private Tenant requireTenant(String tenantKey) {
        if (tenantKey == null || tenantKey.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tenant key must not be blank");
        }

        return tenantRepository.findByTenantKey(tenantKey)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Tenant not found"));
    }
}
