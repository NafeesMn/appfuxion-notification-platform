package com.appfuxion_notification_platform.backend.campaign.persistence;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CampaignRecipientRepository extends JpaRepository<CampaignRecipient, UUID> {

    List<CampaignRecipient> findByCampaignIdAndTenantId(UUID campaignId, UUID tenantId);

    long countByCampaignIdAndTenantId(UUID campaignId, UUID tenantId);
}
