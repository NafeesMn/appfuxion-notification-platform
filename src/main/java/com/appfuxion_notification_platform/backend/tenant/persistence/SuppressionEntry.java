package com.appfuxion_notification_platform.backend.tenant.persistence;

import com.appfuxion_notification_platform.backend.persistence.common.BaseEntity;
import com.appfuxion_notification_platform.backend.tenant.domain.SuppressionChannelScope;

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
@Table(name = "global_suppression_entries")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SuppressionEntry extends BaseEntity {

    @Enumerated(EnumType.STRING)
    @Column(name = "channel_scope", nullable = false, length = 16)
    private SuppressionChannelScope channelScope;

    @Column(name = "suppression_key", nullable = false, length = 512)
    private String suppressionKey;

    @Column(name = "reason_code", length = 64)
    private String reasonCode;

    @Column(name = "source", length = 64)
    private String source;

    @Column(name = "active", nullable = false)
    private boolean active = true;

}
