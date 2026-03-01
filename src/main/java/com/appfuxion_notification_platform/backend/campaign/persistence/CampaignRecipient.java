package com.appfuxion_notification_platform.backend.campaign.persistence;

import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

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
@Table(name = "campaign_recipients")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PUBLIC)
public class CampaignRecipient extends TenantScopedEntity {

    @Column(name = "campaign_id", nullable = false)
    private UUID campaignId;

    @Enumerated(EnumType.STRING)
    @Column(name = "channel", nullable = false, length = 16)
    private NotificationChannel channel;

    @Column(name = "row_number", nullable = false)
    private int rowNumber;

    @Column(name = "normalized_recipient_key", nullable = false, length = 512)
    private String normalizedRecipientKey;

    @Column(name = "email", length = 320)
    private String email;

    @Column(name = "phone_number", length = 32)
    private String phoneNumber;

    @Column(name = "device_token", length = 512)
    private String deviceToken;

    @Column(name = "timezone", nullable = false, length = 64)
    private String timezone;

    @Column(name = "personalization_payload", columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private String personalizationPayload;

    @Column(name = "normalization_status", nullable = false, length = 32)
    private String normalizationStatus;

}
