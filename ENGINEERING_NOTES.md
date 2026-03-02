# ENGINEERING_NOTES.md

## Assessment Context

This repository is being prepared for the Appfuxion take-home assessment: a multi-tenant notification and campaign platform (Email/SMS/Push simulation) with asynchronous processing, retries, idempotency, rate limiting, CSV ingestion, structured logging, and tenant isolation.

## Scope

In scope:

- Lock architecture decisions and tradeoffs.
- Define bounded contexts and system boundaries.
- Lock ambiguous requirements as explicit assumptions.
- Define conceptual domain model (no SQL).
- Define end-to-end async processing flow.
- Define implementation roadmap for Parts 3, 4, and 5.
- Prepare README and architecture diagrams.

Out of scope:

- Spring controllers/services/repositories.
- Flyway SQL migrations.
- Worker implementation.
- Tests, Docker, CI/CD.
- Frontend/mobile components.

## Fixed Technology Choices

- Backend: Java + Spring Boot (current repo scaffold already present)
- Database: PostgreSQL
- Migrations: Flyway
- Initial async strategy: DB-backed worker processing + transactional outbox
- Scaling strategy: Partitioned DB-backed worker processing with partition/shard ownership

### Deployment Shape

- One Spring Boot service (modular monolith) for the assessment.
- Internal bounded contexts enforce separation without early microservice split.
- PostgreSQL is the system-of-record for campaign state, recipient rows, delivery jobs, attempts, and outbox events.
- Worker loops run in the same deployable initially (can be profile-separated later).

### Why Modular Monolith First

- Lowest delivery risk for a take-home while still demonstrating DDD boundaries.
- Easier transactional consistency for campaign creation + outbox.
- Avoids distributed failure modes before core domain behavior is proven.
- Keeps a clean migration path to external broker/workers later by preserving outbox and event-driven state transitions.

## Bounded Contexts (Internal)

### 1. Tenant Governance Context

Responsibilities:

- Tenant metadata and defaults (including `default_timezone`)
- Tenant-scoped policies and quotas (future)
- Global suppression checks (read model / policy adapter)
- Tenant isolation enforcement inputs (tenantId required in all domain operations)

### 2. Campaign Management Context

Responsibilities:

- Campaign lifecycle (`DRAFT/ACCEPTED/PROCESSING/...`)
- Campaign metadata (channel, template/message, mode transactional/marketing, createdBy)
- Campaign progress counters (accepted/skipped/sent/failed/delayed)
- Retry-failures command orchestration

### 3. Audience Ingestion Context

Responsibilities:

- Streaming CSV parsing and validation
- Recipient row normalization (email/phone/device token + timezone fallback)
- Persist campaign recipients row-by-row without loading full file into memory
- Import summary and row-level rejection accounting

### 4. Delivery Orchestration Context

Responsibilities:

- Create notification jobs from campaign recipients
- Job state transitions and scheduling (`nextAttemptAt`)
- Retry policy with exponential backoff
- Idempotency enforcement before provider simulation call
- Delivery attempt tracking and final outcome updates

### 5. Rule Engine Context (Simple, Extensible)

Responsibilities:

- Ordered rule evaluation before dispatch
- Quiet-hours delay rules (SMS/Push)
- Global suppression skip rule
- Future extensibility for tenant-specific rules

### 6. Platform Messaging / Outbox Context

Responsibilities:

- Persist domain events to transactional outbox
- Publish internal work signals by polling outbox rows
- Guarantee state change visibility before async processing begins

### 7. Observability & Operations (Cross-Cutting)

Responsibilities:

- Structured logging with `correlationId`, `tenantId`, `campaignId`, `notificationJobId`, `attempt`
- PII masking in logs
- Metrics hooks (queue depth, throughput, retries, rule outcomes, rate-limit waits)
- Worker lease/partition telemetry

## Key Architecture Decisions and Tradeoffs

### Decision 1: Transactional Outbox + DB-Backed Worker (Now)

Chosen:

- Persist campaign/recipients/jobs and outbox events in PostgreSQL within the same transaction.
- Worker(s) poll DB for due work and outbox events.

Why:

- Satisfies async behavior and crash recovery without introducing Kafka/RabbitMQ complexity.
- Demonstrates production-grade consistency pattern (outbox) in the assessment.
- Keeps future broker migration straightforward (replace outbox poller sink with broker publisher/CDC).

Tradeoff:

- Higher DB load and polling overhead than a broker-first design.
- Requires careful indexes and fetch batching to avoid contention.

### Decision 2: Streaming CSV Ingestion in Request Path

Chosen:

- `POST /campaigns` streams CSV row-by-row, persists recipients incrementally, creates campaign, and enqueues processing.
- API returns `202 Accepted` after ingestion is complete and async processing is scheduled.

Why:

- Meets the explicit requirement to accept CSV upload, store recipients, create campaign record, and enqueue async processing.
- Avoids full-file memory load.
- Simpler than introducing blob storage + async import pipeline in the first implementation.

Tradeoff:

- Request latency scales with CSV size.
- In production, a staged upload + async ingestion pipeline would reduce API latency and improve resilience.

### Decision 3: Deterministic Idempotency Key + Unique Persistence Guard

Chosen:

- Idempotency key: `hash(tenantId + campaignId + recipientId + channel + normalizedMessageTemplate)`.
- Enforce uniqueness in persisted delivery identity

Why:

- Prevents duplicate sends during retries, worker restarts, and lease failover.
- Ties identity to message semantics, allowing distinct campaigns/messages to send independently.

Tradeoff:

- Message normalization must be deterministic and stable.
- Template changes after campaign acceptance must be restricted or versioned.

### Decision 4: Explicit `DELAYED` and `SKIPPED` Notification States

Chosen:

- Extend status model beyond success/failure with:
  - `DELAYED` (quiet-hours deferral)
  - `SKIPPED` (suppression or policy skip)

Why:

- Makes business rules observable and not misclassified as failures.
- Simplifies reporting and retry-failures behavior (retry only retryable failures).

Tradeoff:

- More state transitions to model and test.

### Decision 5: Global Per-Channel Rate Limit (Shared Across Tenants)

Chosen:

- Treat `100 requests/min` as provider limit per channel (`EMAIL`, `SMS`, `PUSH`) globally.

Why:

- Matches the requirement wording ("per channel") and is stricter/safer than per-tenant interpretation.
- Produces clearer throttling/back-pressure behavior for the assessment.

Tradeoff:

- Noisy tenants can consume capacity. Fairness controls are deferred (but documented for production).

### Decision 6: Scaling via Partitioned DB Workers

Chosen:

- Partition notification jobs by a deterministic shard key.
- Workers claim partition leases and poll/process only owned partitions.

Why:

- Demonstrates scaling, back-pressure, and partition ownership without changing the core storage model.
- Reuses the outbox + DB job model from Part 3.

Tradeoff:

- Operational complexity increases (leases, rebalancing, hot partitions).
- DB remains a bottleneck at higher scale vs broker-based fanout.

## Locked Assumptions (Interview-Defensible)

### A1. Recipient Timezone Ambiguity

- CSV supports an optional timezone column.
- If missing, fallback to `tenant.default_timezone`.
- Rationale: quiet-hours rules need recipient-local time; prompt CSV examples may omit timezone.

### A2. Rate Limit Scope

- `100 requests/min` is interpreted as a global provider limit per channel, shared across all tenants.
- Rationale: requirement specifies per channel, not per tenant.

### A3. Idempotency Key Composition

- `hash(tenantId + campaignId + recipientId + channel + normalizedMessageTemplate)`.
- Rationale: avoids duplicate sends while allowing independent campaigns/messages.

### A4. Notification Status Model Extension

- Add `DELAYED` and `SKIPPED`.
- Rationale: quiet-hours and suppression are neither success nor retryable failure.

### A5. Scaling Strategy

- Implement partitioned DB-backed worker processing with partition/shard ownership.
- Rationale: lower implementation risk than broker migration while still demonstrating throughput controls.

## High-Level Processing Flow (Ingestion to Delivery)

### Campaign Creation (`POST /campaigns`)

1. Receive multipart request (campaign metadata + CSV).
2. Resolve tenant context and generate `correlationId`.
3. Validate metadata (channel, template/message, campaign type).
4. Create campaign record in `ACCEPTED` or `INGESTING` state
5. Stream CSV rows:
   - parse row
   - normalize recipient fields
   - apply timezone fallback (`tenant.default_timezone`)
   - validate required columns per channel
   - persist recipient row / import row
   - accumulate import counters
6. Create notification jobs (either inline during ingestion or as a post-ingestion expansion step within same bounded workflow).
7. Insert outbox event (e.g., `CampaignDispatchRequested`) transactionally.
8. Return `202 Accepted` with campaign id and initial summary.

### Worker Processing

1. Poll due notification jobs (`status in PENDING/RETRY_SCHEDULED/DELAYED` and `nextAttemptAt <= now`) with lease/claim semantics.
2. Evaluate rules (ordered):
   - suppression -> `SKIPPED`
   - quiet-hours for SMS/Push (unless transactional) -> `DELAYED` with computed `nextAttemptAt`
   - allow -> continue
3. Check channel throttling (global per-channel budget):
   - if unavailable budget -> requeue with near-future `nextAttemptAt` (back-pressure)
4. Enforce idempotency guard before provider call.
5. Call provider simulator.
6. Persist attempt outcome and state transition:
   - success -> `SENT`
   - retryable failure -> `RETRY_SCHEDULED` with exponential backoff
   - terminal failure -> `FAILED`
7. Update campaign aggregate counters/progress asynchronously or in same transaction depending operation shape.
8. Emit outbox event(s) for state changes as needed.

## Failure Scenarios and Recovery Strategy

### API Crash During CSV Ingestion

- Risk: partial recipient rows persisted.
- Approach:
  - Campaign import state tracks progress and import summary.
  - Transaction boundaries should be chosen to avoid losing all rows on large files (chunked transactions may be used later).
  - If request fails before campaign marked ready, campaign remains in failed/ingestion-error state and is not dispatched.

### Crash After DB Commit but Before Worker Sees Work

- Outbox rows are persisted transactionally with campaign/job state.
- Worker poller eventually reads the outbox / due jobs and continues.
- No lost work if polling resumes.

### Crash After Provider Call but Before Success Persist

- Duplicate-send risk exists without idempotency.
- Mitigation: deterministic idempotency key + persistence guard ensures retried execution does not re-send the same logical notification.

### Provider Throttling / Simulated Outage

- Treat as retryable failure or rate-limit deferral depending signal type.
- Backoff policy applied with max retry count.
- Jobs exceeding max retries become `FAILED`; `retry-failures` endpoint can reschedule.

### Worker Lease/Partition Ownership Loss

- Claimed partitions expire via lease timeout.
- Another worker can reclaim and resume.
- Idempotency prevents duplicate sends during overlap/race conditions.

## Scaling Plan (Part 4 Focus)

### Chosen Strategy

- Partitioned DB-backed worker processing with partition/shard ownership and lease table.

### What This Must Demonstrate

- Back-pressure under channel limits and DB polling pressure
- Partition assignment/ownership
- Bottleneck analysis (DB I/O, polling scans, provider simulation throughput)
- Benchmark/load script and measurable throughput/latency outcomes

### Planned Controls

- `nextAttemptAt` scheduling to avoid hot-spin retries
- bounded poll batch sizes per partition
- worker-local bounded execution queues
- per-channel dispatch budgets
- lease duration + heartbeat for partition ownership
- indexes on tenant/status/partition/nextAttemptAt

### Expected Bottlenecks (Documented Ahead of Time)

- DB polling scans and update contention on hot statuses
- campaign counter updates (write amplification)
- global channel throttle coordination
- large CSV import write throughput

## Part 5 System Design Preparation (10M/day Readiness Topics)

These are intentionally deferred from implementation but will be covered in the Part 5 design write-up:

- DB partitioning strategy (time + tenant/hash hybrid options)
- Data retention and archival for attempts/log-like tables
- Read/write split and queue/offload evolution (Kafka/RabbitMQ)
- Outage behavior (provider outage, DB failover, degraded modes)
- Observability stack (metrics, tracing, alerts, SLOs)
- Security hardening (authN/Z, secret management, encryption, audit)
- Multi-tenant fairness and quota controls

## Logging and PII Masking Guidance

Structured logs should consistently include:

- `correlationId`
- `tenantId`
- `campaignId`
- `notificationJobId` (when applicable)
- `channel`
- `status`
- `attemptNumber`
- `workerId` / `partitionId`

PII masking policy (minimum):

- Email address: mask local part (e.g., `j***@domain.com`)
- Phone number: retain country code/last 2-4 digits only
- Message body/template variables: never log raw body; log template id/version and size/hash

## Testing Strategy

- Unit tests:
  - rule engine decisions (suppression, quiet-hours, transactional bypass)
  - retry backoff calculation
  - idempotency key normalization
- Integration tests (Testcontainers PostgreSQL preferred):
  - campaign creation and async enqueue behavior
  - worker processing state transitions
  - retry-failures endpoint behavior
  - tenant isolation query filters
- Load/benchmark tests (Part 4):
  - throughput under channel throttling
  - partition rebalance and worker failover

## Known Limitations (Deliberate for Assessment)

- Initial implementation will use a single service + DB polling rather than external broker.
- CSV ingestion remains in request path (streamed) instead of staged object storage + async import.
- Provider integrations are simulated only.
- Auth/authz is deferred unless required by assessment rubric.
- Distributed tracing, circuit breakers, and cache layers are documented but deferred.

## Production Changes Deferred (Explicitly)

- External message broker (Kafka/RabbitMQ) or CDC-based outbox publishing
- Object storage for campaign CSV uploads + async import jobs
- Strong tenant authentication/authorization and RBAC
- Provider credential vaulting / secret rotation
- Circuit breakers / bulkheads per provider adapter
- OpenTelemetry traces and centralized log aggregation
- Multi-region failover, DR, and backup validation
- Per-tenant quotas/fair-share scheduling

## AI Assistance Disclosure

Planned approach for assessment submission:

- Use AI assistance for architecture drafting, tradeoff articulation, and documentation quality checks.
- Use AI assistance selectively for boilerplate generation suggestions (not blind copy/paste).
- Manually validate all design decisions, assumptions, and implementation behavior.
- Clearly disclose AI usage in this file and ensure final code reflects deliberate engineering choices.
