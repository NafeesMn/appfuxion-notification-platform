package com.appfuxion_notification_platform.backend.tenant.persistence;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.appfuxion_notification_platform.backend.tenant.domain.SuppressionChannelScope;

public interface SuppressionEntryRepository extends JpaRepository<SuppressionEntry, UUID> {

    List<SuppressionEntry> findByActiveTrueAndSuppressionKeyAndChannelScopeIn(
            String suppressionKey,
            Collection<SuppressionChannelScope> channelScopes);
}
