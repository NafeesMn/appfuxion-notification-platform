-- Phase 1 baseline schema for the multi-tenant notification platform.
-- Scope: durable workflow state, tenant isolation columns, retries/idempotency,
-- outbox, and forward-compatible partition lease table (Part 4).

CREATE TABLE tenants (
    id UUID PRIMARY KEY,
    tenant_key VARCHAR(100) NOT NULL,
    name VARCHAR(200) NOT NULL,
    default_timezone VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_tenants_tenant_key UNIQUE (tenant_key)
);

CREATE TABLE global_suppression_entries (
    id UUID PRIMARY KEY,
    channel_scope VARCHAR(16) NOT NULL,
    suppression_key VARCHAR(512) NOT NULL,
    reason_code VARCHAR(64),
    source VARCHAR(64),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_global_suppression_entries_scope_key UNIQUE (channel_scope, suppression_key)
);

CREATE TABLE campaigns (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    correlation_id UUID NOT NULL,
    channel VARCHAR(16) NOT NULL,
    campaign_type VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL,
    message_template TEXT NOT NULL,
    normalized_message_hash VARCHAR(128) NOT NULL,
    created_by VARCHAR(128),
    recipient_count INTEGER NOT NULL DEFAULT 0,
    sent_count INTEGER NOT NULL DEFAULT 0,
    failed_count INTEGER NOT NULL DEFAULT 0,
    skipped_count INTEGER NOT NULL DEFAULT 0,
    delayed_count INTEGER NOT NULL DEFAULT 0,
    invalid_row_count INTEGER NOT NULL DEFAULT 0,
    import_started_at TIMESTAMPTZ,
    import_completed_at TIMESTAMPTZ,
    dispatch_requested_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_campaigns_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id),
    CONSTRAINT ck_campaign_counts_non_negative CHECK (
        recipient_count >= 0
        AND sent_count >= 0
        AND failed_count >= 0
        AND skipped_count >= 0
        AND delayed_count >= 0
        AND invalid_row_count >= 0
    )
);

CREATE TABLE campaign_recipients (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    campaign_id UUID NOT NULL,
    channel VARCHAR(16) NOT NULL,
    row_number INTEGER NOT NULL,
    normalized_recipient_key VARCHAR(512) NOT NULL,
    email VARCHAR(320),
    phone_number VARCHAR(32),
    device_token VARCHAR(512),
    timezone VARCHAR(64) NOT NULL,
    personalization_payload JSONB,
    normalization_status VARCHAR(32) NOT NULL DEFAULT 'ACCEPTED',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_campaign_recipients_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id),
    CONSTRAINT fk_campaign_recipients_campaign FOREIGN KEY (campaign_id) REFERENCES campaigns (id),
    CONSTRAINT uq_campaign_recipients_campaign_row UNIQUE (campaign_id, row_number)
);

CREATE TABLE campaign_import_row_errors (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    campaign_id UUID NOT NULL,
    row_number INTEGER NOT NULL,
    error_code VARCHAR(64) NOT NULL,
    error_message TEXT,
    masked_row_snapshot JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_campaign_import_row_errors_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id),
    CONSTRAINT fk_campaign_import_row_errors_campaign FOREIGN KEY (campaign_id) REFERENCES campaigns (id)
);

CREATE TABLE notification_jobs (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    campaign_id UUID NOT NULL,
    campaign_recipient_id UUID NOT NULL,
    correlation_id UUID NOT NULL,
    channel VARCHAR(16) NOT NULL,
    status VARCHAR(32) NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL,
    partition_key INTEGER NOT NULL DEFAULT 0,
    attempt_count INTEGER NOT NULL DEFAULT 0,
    max_retries INTEGER NOT NULL DEFAULT 3,
    next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    last_attempt_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,
    deferred_until TIMESTAMPTZ,
    last_error_code VARCHAR(64),
    last_error_message TEXT,
    last_rule_reason_code VARCHAR(64),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_notification_jobs_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id),
    CONSTRAINT fk_notification_jobs_campaign FOREIGN KEY (campaign_id) REFERENCES campaigns (id),
    CONSTRAINT fk_notification_jobs_campaign_recipient FOREIGN KEY (campaign_recipient_id) REFERENCES campaign_recipients (id),
    CONSTRAINT uq_notification_jobs_tenant_idempotency UNIQUE (tenant_id, idempotency_key),
    CONSTRAINT ck_notification_jobs_retry_values CHECK (attempt_count >= 0 AND max_retries >= 0)
);

CREATE TABLE notification_attempts (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    campaign_id UUID NOT NULL,
    notification_job_id UUID NOT NULL,
    attempt_number INTEGER NOT NULL,
    worker_id VARCHAR(128),
    partition_id INTEGER,
    outcome VARCHAR(32) NOT NULL,
    error_code VARCHAR(64),
    provider_request_id VARCHAR(128),
    provider_response_code VARCHAR(64),
    latency_ms INTEGER,
    request_metadata JSONB,
    response_metadata JSONB,
    started_at TIMESTAMPTZ NOT NULL,
    completed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_notification_attempts_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id),
    CONSTRAINT fk_notification_attempts_campaign FOREIGN KEY (campaign_id) REFERENCES campaigns (id),
    CONSTRAINT fk_notification_attempts_job FOREIGN KEY (notification_job_id) REFERENCES notification_jobs (id),
    CONSTRAINT uq_notification_attempts_job_attempt UNIQUE (notification_job_id, attempt_number),
    CONSTRAINT ck_notification_attempts_latency_non_negative CHECK (latency_ms IS NULL OR latency_ms >= 0)
);

CREATE TABLE outbox_events (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    aggregate_type VARCHAR(64) NOT NULL,
    aggregate_id UUID NOT NULL,
    event_type VARCHAR(128) NOT NULL,
    payload JSONB NOT NULL,
    status VARCHAR(32) NOT NULL,
    available_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    published_at TIMESTAMPTZ,
    retry_count INTEGER NOT NULL DEFAULT 0,
    error_message TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_outbox_events_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id),
    CONSTRAINT ck_outbox_events_retry_non_negative CHECK (retry_count >= 0)
);

CREATE TABLE worker_partition_leases (
    partition_id INTEGER PRIMARY KEY,
    worker_id VARCHAR(128) NOT NULL,
    lease_expires_at TIMESTAMPTZ NOT NULL,
    heartbeat_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_worker_partition_leases_version_non_negative CHECK (version >= 0)
);

-- Tenant and policy indexes
CREATE INDEX idx_global_suppression_entries_active_scope_key
    ON global_suppression_entries (active, channel_scope, suppression_key);

-- Campaign/query indexes
CREATE INDEX idx_campaigns_tenant_status_created_at
    ON campaigns (tenant_id, status, created_at DESC);
CREATE INDEX idx_campaigns_tenant_created_at
    ON campaigns (tenant_id, created_at DESC);

-- Recipient/import indexes
CREATE INDEX idx_campaign_recipients_tenant_campaign
    ON campaign_recipients (tenant_id, campaign_id);
CREATE INDEX idx_campaign_recipients_campaign
    ON campaign_recipients (campaign_id);
CREATE INDEX idx_campaign_recipients_tenant_norm_key
    ON campaign_recipients (tenant_id, normalized_recipient_key);

CREATE INDEX idx_campaign_import_row_errors_tenant_campaign
    ON campaign_import_row_errors (tenant_id, campaign_id);
CREATE INDEX idx_campaign_import_row_errors_campaign_row
    ON campaign_import_row_errors (campaign_id, row_number);

-- Job polling / retry / reporting indexes
CREATE INDEX idx_notification_jobs_status_next_attempt_at
    ON notification_jobs (status, next_attempt_at, created_at);
CREATE INDEX idx_notification_jobs_tenant_status_next_attempt_at
    ON notification_jobs (tenant_id, status, next_attempt_at);
CREATE INDEX idx_notification_jobs_partition_status_next_attempt_at
    ON notification_jobs (partition_key, status, next_attempt_at);
CREATE INDEX idx_notification_jobs_campaign_status
    ON notification_jobs (campaign_id, status);
CREATE INDEX idx_notification_jobs_campaign_recipient
    ON notification_jobs (campaign_recipient_id);

-- Attempt history indexes
CREATE INDEX idx_notification_attempts_job_created_at
    ON notification_attempts (notification_job_id, created_at DESC);
CREATE INDEX idx_notification_attempts_tenant_campaign
    ON notification_attempts (tenant_id, campaign_id);
CREATE INDEX idx_notification_attempts_outcome
    ON notification_attempts (outcome, created_at DESC);

-- Outbox polling indexes
CREATE INDEX idx_outbox_events_status_available_created
    ON outbox_events (status, available_at, created_at);
CREATE INDEX idx_outbox_events_tenant_status_created
    ON outbox_events (tenant_id, status, created_at);
CREATE INDEX idx_outbox_events_aggregate
    ON outbox_events (aggregate_type, aggregate_id);

-- Phase 4 forward-compatibility indexes
CREATE INDEX idx_worker_partition_leases_lease_expires_at
    ON worker_partition_leases (lease_expires_at);
