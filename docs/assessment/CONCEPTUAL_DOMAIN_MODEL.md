# Conceptual Domain Model (No SQL)

## Summary

This document defines the conceptual domain model and bounded contexts for the multi-tenant notification/campaign platform. It is intentionally implementation-ready but database-agnostic (no table definitions or SQL yet).

## Bounded Contexts and Core Aggregates

### 1. Tenant Governance Context

#### Aggregate: Tenant

Purpose:

- Owns tenant-level configuration and defaults used by all other contexts.

Core concepts:

- `tenantId`
- `name`
- `defaultTimezone`
- `status` (active/suspended)
- future: quotas, plan tier, feature flags

#### Entity: SuppressionEntry (Global Suppression List)

Purpose:

- Represents globally suppressed recipients (email/phone/device token) that must be skipped.

Core concepts:

- `suppressionKey` (normalized channel address/token)
- `channel` (or `ANY`)
- `reason`
- `source`
- `active`

Notes:

- "Global" means shared across tenants for the assessment requirement.
- Can be modeled in Tenant Governance or a shared policy context; consumed read-only by rule engine.

### 2. Campaign Management Context

#### Aggregate: Campaign

Purpose:

- Represents a tenant's campaign request and aggregate lifecycle/progress.

Core concepts:

- `campaignId`
- `tenantId`
- `channel` (`EMAIL|SMS|PUSH`)
- `campaignType` (`TRANSACTIONAL|MARKETING`, naming can vary)
- `messageTemplate` / content descriptor (or template id + variables schema)
- `status` (conceptual: `ACCEPTED`, `QUEUED`, `PROCESSING`, `COMPLETED`, `COMPLETED_WITH_FAILURES`, `FAILED_IMPORT`)
- `createdAt`, `createdBy`
- progress counters:
  - `recipientCount`
  - `sentCount`
  - `failedCount`
  - `skippedCount`
  - `delayedCount`
  - `invalidRowCount`

Invariants:

- Campaign belongs to exactly one tenant.
- Campaign content is immutable after dispatch is enqueued (Assumption A6).

#### Value Object: CampaignContent

Purpose:

- Represents normalized sendable content used for idempotency and provider request generation.

Core concepts:

- `channel`
- `templateId` or `messageBody`
- normalized representation/hash
- optional metadata (subject/title, personalization placeholders)

### 3. Audience Ingestion Context

#### Entity: CampaignRecipient

Purpose:

- Represents a recipient row accepted from the uploaded CSV for a specific campaign.

Core concepts:

- `campaignRecipientId`
- `campaignId`
- `tenantId`
- recipient identity fields by channel:
  - `email`
  - `phoneNumber`
  - `deviceToken`
- `timezone` (explicit or fallback effective timezone)
- personalization payload (optional JSON/map later)
- `rowNumber`
- normalization status / validation outcome

Notes:

- One campaign can have many recipient rows.
- Recipient identity may be denormalized per campaign to preserve upload-time snapshot.

#### Entity: ImportRowError (Optional but Recommended)

Purpose:

- Stores invalid/rejected CSV rows for visibility and debugging without blocking entire campaign.

Core concepts:

- `campaignId`
- `tenantId`
- `rowNumber`
- `errorCode`
- masked/raw-lite row snapshot (PII-safe)

### 4. Delivery Orchestration Context

#### Aggregate: NotificationJob

Purpose:

- Represents one logical notification send for one campaign recipient over one channel.

Core concepts:

- `notificationJobId`
- `tenantId`
- `campaignId`
- `campaignRecipientId`
- `channel`
- `status`
  - conceptual set includes:
    - `PENDING`
    - `PROCESSING`
    - `SENT`
    - `FAILED`
    - `RETRY_SCHEDULED`
    - `DELAYED`
    - `SKIPPED`
- `attemptCount`
- `maxRetries`
- `nextAttemptAt`
- `lastErrorCode`
- `lastErrorMessage` (PII-safe)
- `idempotencyKey`
- `partitionKey` (reserved for Part 4 scaling)

Invariants:

- A job belongs to one tenant/campaign/recipient.
- Idempotency key must deterministically represent the logical send identity.
- Terminal statuses (`SENT`, `SKIPPED`) are not retried automatically.

#### Entity: NotificationAttempt

Purpose:

- Append-only execution history for a notification job.

Core concepts:

- `notificationAttemptId`
- `notificationJobId`
- `attemptNumber`
- `startedAt`, `completedAt`
- `workerId`
- `partitionId` (Phase 4)
- provider request/response metadata (PII-safe)
- `outcome` (`SUCCESS`, `RETRYABLE_FAILURE`, `TERMINAL_FAILURE`, `THROTTLED`, `RULE_DELAYED`, `RULE_SKIPPED`)
- `errorCode`
- `latencyMs`

#### Entity / Value Object: RetryPolicySnapshot (Conceptual)

Purpose:

- Captures retry/backoff settings used by a job (max retries, base delay, multiplier).

Notes:

- Could be modeled as columns on `NotificationJob` rather than a separate entity.

#### Entity: IdempotencyRecord (Optional Physical Separation)

Purpose:

- Persistent uniqueness guard for deduped sends if not stored directly on `NotificationJob`.

Notes:

- Physical design may instead use a unique constraint on `NotificationJob.idempotencyKey`.
- Keep conceptual separation to emphasize responsibility.

### 5. Rules / Policy Context

#### Value Object: RuleDecision

Purpose:

- Standardized result of rule evaluation before dispatch.

Core concepts:

- `decisionType` (`ALLOW`, `SKIP`, `DELAY`)
- `reasonCode` (e.g., `GLOBAL_SUPPRESSION`, `QUIET_HOURS`)
- `nextEligibleAt` (for delay decisions)
- optional metadata (timezone used, quiet-hours window)

#### Service (Domain Service): RuleEvaluator

Responsibilities:

- Execute ordered rules using campaign, recipient, tenant, and current time.
- Return first terminal decision or `ALLOW`.

Ordered rules (initial):

1. Global suppression
2. Quiet hours (SMS/Push only, bypass if transactional)

### 6. Platform Messaging / Outbox Context

#### Aggregate: OutboxEvent

Purpose:

- Durable event record written in the same transaction as domain state changes.

Core concepts:

- `outboxEventId`
- `aggregateType`
- `aggregateId`
- `eventType`
- `tenantId`
- `payload` (compact JSON)
- `status` (`NEW`, `PUBLISHED`, `FAILED`, optional)
- `createdAt`, `publishedAt`
- `retryCount`

Example event types:

- `CampaignDispatchRequested`
- `NotificationJobCreated`
- `NotificationJobStatusChanged`
- `CampaignRetryFailuresRequested`

Notes:

- In a single-service setup, outbox events may drive internal worker workflows.
- Model should remain compatible with future broker publishing.

### 7. Operations / Scaling Context (Phase 4-Oriented, Conceptual in Phase 0)

#### Entity: WorkerPartitionLease

Purpose:

- Coordinates ownership of partitions/shards across workers.

Core concepts:

- `partitionId`
- `workerId`
- `leaseExpiresAt`
- `heartbeatAt`
- `version` (for optimistic concurrency)

#### Entity / Value Object: ChannelRateBudgetState

Purpose:

- Represents effective per-channel dispatch budget and throttling metadata.

Core concepts:

- `channel`
- `windowStart`
- `requestsUsed`
- `limitPerMinute` (`100`)

Notes:

- Physical implementation may use DB rows, queries over attempts, or worker coordination strategy.
- Conceptually required because rate limit is global per channel.

## Relationships (Conceptual)

- `Tenant` 1 -> many `Campaign`
- `Campaign` 1 -> many `CampaignRecipient`
- `Campaign` 1 -> many `NotificationJob`
- `CampaignRecipient` 1 -> 1..many `NotificationJob` (future multi-channel expansion may increase cardinality)
- `NotificationJob` 1 -> many `NotificationAttempt`
- Domain aggregates -> many `OutboxEvent` (via state changes)
- `WorkerPartitionLease` coordinates processing of subsets of `NotificationJob`
- `RuleEvaluator` reads `SuppressionEntry` and campaign/recipient/tenant context to return `RuleDecision`

## State Models (Conceptual)

### Campaign Lifecycle (High-Level)

Suggested conceptual states:

- `ACCEPTED` / `INGESTING`
- `READY_FOR_DISPATCH`
- `PROCESSING`
- `COMPLETED`
- `COMPLETED_WITH_FAILURES`
- `FAILED_IMPORT`

Notes:

- Final names may be simplified in schema phase.
- Campaign state is aggregate-level; notification jobs hold detailed execution status.

### NotificationJob Lifecycle (High-Level)

- `PENDING` -> `PROCESSING` -> `SENT`
- `PENDING` -> `PROCESSING` -> `FAILED` (terminal)
- `PENDING` -> `PROCESSING` -> `RETRY_SCHEDULED` -> `PROCESSING` ...
- `PENDING` -> `DELAYED` -> `PENDING/PROCESSING` (implementation naming may vary)
- `PENDING/PROCESSING` -> `SKIPPED`

Important distinction:

- `DELAYED` is policy deferral (quiet hours), not failure.
- `RETRY_SCHEDULED` is failure-driven backoff state.

## Domain Events (Conceptual, EDA-Style State Changes)

Initial internal event candidates:

- `CampaignAccepted`
- `CampaignIngestionCompleted`
- `CampaignDispatchRequested`
- `NotificationJobEnqueued`
- `NotificationJobDelayed`
- `NotificationJobSkipped`
- `NotificationAttemptCompleted`
- `NotificationJobFailed`
- `NotificationJobSent`
- `CampaignProgressRecalculated`

These are not event-sourcing records; they are domain/integration events used to decouple workflows while retaining a relational source of truth.

## Phase 1 Schema Design Implications

To keep Part 4 and reporting viable, Phase 1 schema should reserve/support:

- tenant-scoped keys and indexes on all core entities
- due-work polling fields (`status`, `nextAttemptAt`)
- idempotency uniqueness
- attempt history table
- outbox table
- optional partition key and lease tables (even if activated later)

This allows implementation to proceed without revisiting core modeling decisions.
