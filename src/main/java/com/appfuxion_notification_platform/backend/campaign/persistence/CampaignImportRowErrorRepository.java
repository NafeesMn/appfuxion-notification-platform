package com.appfuxion_notification_platform.backend.campaign.persistence;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CampaignImportRowErrorRepository extends JpaRepository<CampaignImportRowError, UUID> {

    List<CampaignImportRowError> findByCampaignIdAndTenantIdOrderByRowNumberAsc(UUID campaignId, UUID tenantId);
}
