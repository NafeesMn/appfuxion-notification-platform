package com.appfuxion_notification_platform.backend.delivery.persistence;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.appfuxion_notification_platform.backend.delivery.domain.NotificationAttemptOutcome;
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
@Table(name = "notification_attempts")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PUBLIC)
public class NotificationAttempt extends TenantScopedEntity {

    @Column(name = "campaign_id", nullable = false)
    private UUID campaignId;

    @Column(name = "notification_job_id", nullable = false)
    private UUID notificationJobId;

    @Column(name = "attempt_number", nullable = false)
    private int attemptNumber;

    @Column(name = "worker_id", length = 128)
    private String workerId;

    @Column(name = "partition_id")
    private Integer partitionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "outcome", nullable = false, length = 32)
    private NotificationAttemptOutcome outcome;

    @Column(name = "error_code", length = 64)
    private String errorCode;

    @Column(name = "provider_request_id", length = 128)
    private String providerRequestId;

    @Column(name = "provider_response_code", length = 64)
    private String providerResponseCode;

    @Column(name = "latency_ms")
    private Integer latencyMs;

    @Column(name = "request_metadata", columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private String requestMetadata;

    @Column(name = "response_metadata", columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private String responseMetadata;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

}
