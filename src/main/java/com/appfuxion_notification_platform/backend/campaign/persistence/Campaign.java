package com.appfuxion_notification_platform.backend.campaign.persistence;

import java.time.Instant;
import java.util.UUID;

import com.appfuxion_notification_platform.backend.campaign.domain.CampaignStatus;
import com.appfuxion_notification_platform.backend.domain.shared.CampaignType;
import com.appfuxion_notification_platform.backend.domain.shared.NotificationChannel;
import com.appfuxion_notification_platform.backend.persistence.common.TenantScopedEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "campaigns")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PUBLIC)
public class Campaign extends TenantScopedEntity {

    @Column(name = "correlation_id", nullable = false)
    private UUID correlationId;

    @Enumerated(EnumType.STRING)
    @Column(name = "channel", nullable = false, length = 16)
    private NotificationChannel channel;

    @Enumerated(EnumType.STRING)
    @Column(name = "campaign_type", nullable = false, length = 32)
    private CampaignType campaignType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private CampaignStatus status;

    @Column(name = "message_template", nullable = false, columnDefinition = "TEXT")
    private String messageTemplate;

    @Column(name = "normalized_message_hash", nullable = false, length = 128)
    private String normalizedMessageHash;

    @Column(name = "created_by", length = 128)
    private String createdBy;

    @Column(name = "recipient_count", nullable = false)
    private int recipientCount;

    @Column(name = "sent_count", nullable = false)
    private int sentCount;

    @Column(name = "failed_count", nullable = false)
    private int failedCount;

    @Column(name = "skipped_count", nullable = false)
    private int skippedCount;

    @Column(name = "delayed_count", nullable = false)
    private int delayedCount;

    @Column(name = "invalid_row_count", nullable = false)
    private int invalidRowCount;

    @Column(name = "import_started_at")
    private Instant importStartedAt;

    @Column(name = "import_completed_at")
    private Instant importCompletedAt;

    @Column(name = "dispatch_requested_at")
    private Instant dispatchRequestedAt;

}
