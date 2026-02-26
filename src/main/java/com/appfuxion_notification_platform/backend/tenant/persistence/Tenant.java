package com.appfuxion_notification_platform.backend.tenant.persistence;

import com.appfuxion_notification_platform.backend.persistence.common.BaseEntity;
import com.appfuxion_notification_platform.backend.tenant.domain.TenantStatus;

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
@Table(name = "tenants")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Tenant extends BaseEntity {

    @Column(name = "tenant_key", nullable = false, length = 100)
    private String tenantKey;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Column(name = "default_timezone", nullable = false, length = 64)
    private String defaultTimezone;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private TenantStatus status;

}
