package com.appfuxion_notification_platform.backend.campaign.persistence;

import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.appfuxion_notification_platform.backend.persistence.common.TenantScopedEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "campaign_import_row_errors")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PUBLIC)
public class CampaignImportRowError extends TenantScopedEntity {

    @Column(name = "campaign_id", nullable = false)
    private UUID campaignId;

    @Column(name = "row_number", nullable = false)
    private int rowNumber;

    @Column(name = "error_code", nullable = false, length = 64)
    private String errorCode;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "masked_row_snapshot", columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private String maskedRowSnapshot;

}
