package com.appfuxion_notification_platform.backend.campaign.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import com.appfuxion_notification_platform.backend.api.campaign.dto.CampaignDetailResponse;
import com.appfuxion_notification_platform.backend.api.campaign.dto.CampaignListResponse;
import com.appfuxion_notification_platform.backend.campaign.domain.CampaignStatus;
import com.appfuxion_notification_platform.backend.campaign.persistence.Campaign;
import com.appfuxion_notification_platform.backend.campaign.persistence.CampaignImportRowErrorRepository;
import com.appfuxion_notification_platform.backend.campaign.persistence.CampaignRecipientRepository;
import com.appfuxion_notification_platform.backend.campaign.persistence.CampaignRepository;
import com.appfuxion_notification_platform.backend.delivery.domain.NotificationJobStatus;
import com.appfuxion_notification_platform.backend.delivery.persistence.NotificationJobRepository;
import com.appfuxion_notification_platform.backend.domain.shared.CampaignType;
import com.appfuxion_notification_platform.backend.domain.shared.NotificationChannel;
import com.appfuxion_notification_platform.backend.tenant.persistence.Tenant;
import com.appfuxion_notification_platform.backend.tenant.persistence.TenantRepository;

class DefaultCampaignQueryServiceTest {

    @Test
    void listCampaigns_shouldReturnTenantScopedSummaryWithDeliveryCounts() {
        TenantRepository tenantRepository = Mockito.mock(TenantRepository.class);
        CampaignRepository campaignRepository = Mockito.mock(CampaignRepository.class);
        CampaignRecipientRepository campaignRecipientRepository = Mockito.mock(CampaignRecipientRepository.class);
        CampaignImportRowErrorRepository campaignImportRowErrorRepository = Mockito.mock(CampaignImportRowErrorRepository.class);
        NotificationJobRepository notificationJobRepository = Mockito.mock(NotificationJobRepository.class);

        DefaultCampaignQueryService service = new DefaultCampaignQueryService(
                tenantRepository,
                campaignRepository,
                campaignRecipientRepository,
                campaignImportRowErrorRepository,
                notificationJobRepository);

        UUID tenantId = UUID.randomUUID();
        UUID campaignId = UUID.randomUUID();
        Tenant tenant = Mockito.mock(Tenant.class);
        Campaign campaign = Mockito.mock(Campaign.class);

        when(tenant.getId()).thenReturn(tenantId);
        when(tenantRepository.findByTenantKey("tenant-acme")).thenReturn(Optional.of(tenant));
        when(campaign.getId()).thenReturn(campaignId);
        when(campaign.getChannel()).thenReturn(NotificationChannel.EMAIL);
        when(campaign.getCampaignType()).thenReturn(CampaignType.MARKETING);
        when(campaign.getStatus()).thenReturn(CampaignStatus.PROCESSING);
        when(campaign.getCreatedAt()).thenReturn(Instant.parse("2026-03-01T10:00:00Z"));
        when(campaignRepository.findByTenantId(tenantId, PageRequest.of(0, 20, org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC, "createdAt"))))
                .thenReturn(new PageImpl<>(List.of(campaign), PageRequest.of(0, 20), 1));

        when(notificationJobRepository.countByCampaignIdAndTenantId(campaignId, tenantId)).thenReturn(12L);
        when(notificationJobRepository.countByCampaignIdAndTenantIdAndStatus(campaignId, tenantId, NotificationJobStatus.SENT)).thenReturn(6L);
        when(notificationJobRepository.countByCampaignIdAndTenantIdAndStatus(campaignId, tenantId, NotificationJobStatus.FAILED)).thenReturn(2L);
        when(notificationJobRepository.countByCampaignIdAndTenantIdAndStatus(campaignId, tenantId, NotificationJobStatus.SKIPPED)).thenReturn(1L);
        when(notificationJobRepository.countByCampaignIdAndTenantIdAndStatus(campaignId, tenantId, NotificationJobStatus.DELAYED)).thenReturn(1L);
        when(notificationJobRepository.countByCampaignIdAndTenantIdAndStatusIn(any(), any(), any())).thenReturn(2L);

        CampaignListResponse response = service.listCampaigns("tenant-acme", 0, 20, null, null);

        assertEquals(1, response.items().size());
        assertEquals(12, response.items().get(0).deliverySummary().total());
        assertEquals(2, response.items().get(0).deliverySummary().pending());
    }

    @Test
    void getCampaign_shouldReturnDetailIncludingImportAndDeliverySummary() {
        TenantRepository tenantRepository = Mockito.mock(TenantRepository.class);
        CampaignRepository campaignRepository = Mockito.mock(CampaignRepository.class);
        CampaignRecipientRepository campaignRecipientRepository = Mockito.mock(CampaignRecipientRepository.class);
        CampaignImportRowErrorRepository campaignImportRowErrorRepository = Mockito.mock(CampaignImportRowErrorRepository.class);
        NotificationJobRepository notificationJobRepository = Mockito.mock(NotificationJobRepository.class);

        DefaultCampaignQueryService service = new DefaultCampaignQueryService(
                tenantRepository,
                campaignRepository,
                campaignRecipientRepository,
                campaignImportRowErrorRepository,
                notificationJobRepository);

        UUID tenantId = UUID.randomUUID();
        UUID campaignId = UUID.randomUUID();
        Tenant tenant = Mockito.mock(Tenant.class);
        Campaign campaign = Mockito.mock(Campaign.class);

        when(tenant.getId()).thenReturn(tenantId);
        when(tenantRepository.findByTenantKey("tenant-acme")).thenReturn(Optional.of(tenant));
        when(campaignRepository.findByIdAndTenantId(campaignId, tenantId)).thenReturn(Optional.of(campaign));
        when(campaign.getId()).thenReturn(campaignId);
        when(campaign.getCorrelationId()).thenReturn(UUID.randomUUID());
        when(campaign.getChannel()).thenReturn(NotificationChannel.SMS);
        when(campaign.getCampaignType()).thenReturn(CampaignType.MARKETING);
        when(campaign.getStatus()).thenReturn(CampaignStatus.PROCESSING);
        when(campaign.getMessageTemplate()).thenReturn("Hello");
        when(campaign.getCreatedBy()).thenReturn("tester");
        when(campaign.getCreatedAt()).thenReturn(Instant.parse("2026-03-01T10:00:00Z"));
        when(campaign.getUpdatedAt()).thenReturn(Instant.parse("2026-03-01T10:05:00Z"));

        when(campaignRecipientRepository.countByCampaignIdAndTenantId(campaignId, tenantId)).thenReturn(10L);
        when(campaignImportRowErrorRepository.countByCampaignIdAndTenantId(campaignId, tenantId)).thenReturn(2L);
        when(notificationJobRepository.countByCampaignIdAndTenantId(campaignId, tenantId)).thenReturn(10L);
        when(notificationJobRepository.countByCampaignIdAndTenantIdAndStatus(campaignId, tenantId, NotificationJobStatus.SENT)).thenReturn(7L);
        when(notificationJobRepository.countByCampaignIdAndTenantIdAndStatus(campaignId, tenantId, NotificationJobStatus.FAILED)).thenReturn(1L);
        when(notificationJobRepository.countByCampaignIdAndTenantIdAndStatus(campaignId, tenantId, NotificationJobStatus.SKIPPED)).thenReturn(1L);
        when(notificationJobRepository.countByCampaignIdAndTenantIdAndStatus(campaignId, tenantId, NotificationJobStatus.DELAYED)).thenReturn(0L);
        when(notificationJobRepository.countByCampaignIdAndTenantIdAndStatusIn(any(), any(), any())).thenReturn(1L);

        CampaignDetailResponse response = service.getCampaign("tenant-acme", campaignId);

        assertEquals(12, response.importSummary().totalRows());
        assertEquals(10, response.importSummary().acceptedRows());
        assertEquals(2, response.importSummary().invalidRows());
        assertEquals(10, response.deliverySummary().total());
        assertEquals(1, response.deliverySummary().pending());
    }

    @Test
    void getCampaign_shouldThrowNotFoundWhenCampaignIsMissing() {
        TenantRepository tenantRepository = Mockito.mock(TenantRepository.class);
        CampaignRepository campaignRepository = Mockito.mock(CampaignRepository.class);
        CampaignRecipientRepository campaignRecipientRepository = Mockito.mock(CampaignRecipientRepository.class);
        CampaignImportRowErrorRepository campaignImportRowErrorRepository = Mockito.mock(CampaignImportRowErrorRepository.class);
        NotificationJobRepository notificationJobRepository = Mockito.mock(NotificationJobRepository.class);

        DefaultCampaignQueryService service = new DefaultCampaignQueryService(
                tenantRepository,
                campaignRepository,
                campaignRecipientRepository,
                campaignImportRowErrorRepository,
                notificationJobRepository);

        UUID tenantId = UUID.randomUUID();
        Tenant tenant = Mockito.mock(Tenant.class);
        when(tenant.getId()).thenReturn(tenantId);
        when(tenantRepository.findByTenantKey("tenant-acme")).thenReturn(Optional.of(tenant));
        when(campaignRepository.findByIdAndTenantId(any(), any())).thenReturn(Optional.empty());

        ResponseStatusException ex = org.junit.jupiter.api.Assertions.assertThrows(
                ResponseStatusException.class,
                () -> service.getCampaign("tenant-acme", UUID.randomUUID()));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
    }
}
