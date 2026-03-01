package com.appfuxion_notification_platform.backend.campaign.persistence;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.appfuxion_notification_platform.backend.campaign.domain.CampaignStatus;
import com.appfuxion_notification_platform.backend.domain.shared.NotificationChannel;

public interface CampaignRepository extends JpaRepository<Campaign, UUID> {

    Page<Campaign> findByTenantId(UUID tenantId, Pageable pageable);

    Page<Campaign> findByTenantIdAndStatus(UUID tenantId, CampaignStatus status, Pageable pageable);

    Page<Campaign> findByTenantIdAndChannel(UUID tenantId, NotificationChannel channel, Pageable pageable);

    Page<Campaign> findByTenantIdAndStatusAndChannel(
            UUID tenantId,
            CampaignStatus status,
            NotificationChannel channel,
            Pageable pageable);

    Optional<Campaign> findByIdAndTenantId(UUID id, UUID tenantId);
}
