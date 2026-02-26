# Architecture Decisions (ADR-Style)

## Summary

This file records the key architecture decisions for the assessment implementation. Decisions are intentionally pragmatic and optimized for correctness, observability, and interview-defensible tradeoffs in a take-home setting.

## ADR-001 - Use a Modular Monolith (Spring Boot) for the Assessment

- Status: Accepted

### Context

The assessment requires multi-tenant notification orchestration, async processing, retries, idempotency, rate limiting, and system design depth. A microservice split would add operational complexity without improving demonstration value for the core domain.

### Decision

Implement one Spring Boot service with clear internal bounded contexts and package/module boundaries.

### Consequences

- Pros:
  - Simpler local setup and delivery.
  - Easier transactional consistency for campaign creation + outbox.
  - Faster iteration on domain logic.
- Cons:
  - Worker and API concerns share runtime resources unless profile-separated.
  - Less runtime isolation than a multi-service deployment.

### Alternatives Rejected

- Early microservices split (API service + worker service + ingestion service):
  - Rejected due to setup complexity, duplicate configs, and limited assessment time.

## ADR-002 - PostgreSQL as System of Record + Flyway for Migrations

- Status: Accepted

### Context

The platform needs durable workflow state, tenant-scoped querying, retries, idempotency guarantees, and operational visibility. PostgreSQL supports transactional consistency and indexing needs well.

### Decision

Use PostgreSQL for all core domain state and Flyway for versioned schema migrations.

### Consequences

- Pros:
  - Strong transactional semantics for outbox pattern.
  - Mature indexing and query support for polling and reporting.
  - Flyway provides deterministic schema evolution.
- Cons:
  - DB becomes both system-of-record and queue substrate (higher load).

### Alternatives Rejected

- NoSQL-first queue/state design:
  - Rejected due to relational reporting and transactional outbox needs.
- Liquibase:
  - Rejected because Flyway is preselected and sufficient for this scope.

## ADR-003 - Transactional Outbox for Async State Change Propagation

- Status: Accepted

### Context

Campaign creation must return quickly and enqueue async processing. The system also needs crash-safe state transitions and a path to future broker integration.

### Decision

Persist domain state changes and an outbox event in the same transaction. Worker/poller consumes outbox and/or due jobs from DB.

### Consequences

- Pros:
  - Eliminates lost-message risk between DB commit and enqueue.
  - Future-compatible with broker/CDC migration.
  - Demonstrates production-grade consistency pattern.
- Cons:
  - Requires polling infrastructure and outbox lifecycle management.

### Alternatives Rejected

- Direct in-memory async task submission after DB write:
  - Rejected due to lost-work risk on crash/restart.
- Broker-first (Kafka/RabbitMQ) for initial implementation:
  - Rejected due to extra infra complexity and setup overhead for the assessment.

## ADR-004 - Streaming CSV Ingestion in Request Path for `POST /campaigns`

- Status: Accepted

### Context

The prompt explicitly requires CSV upload, recipient storage, campaign creation, and async enqueue. It also requires streaming (no full-file load).

### Decision

Perform CSV parsing/validation in the API request path using streaming row-by-row ingestion. Persist recipients as rows are processed. Return `202 Accepted` after ingestion and dispatch scheduling completes.

### Consequences

- Pros:
  - Meets required behavior with bounded memory usage.
  - Simpler than staging file upload in object storage for the take-home.
- Cons:
  - Request latency grows with file size.
  - Partial-ingestion error handling must be explicit.

### Alternatives Rejected

- Upload to object storage + async import job:
  - Rejected for initial scope due to extra infrastructure and longer implementation path.

## ADR-005 - Notification Delivery as Durable Jobs with Attempts

- Status: Accepted

### Context

Retries, backoff, idempotency, status tracking, and reporting require more than a single "notification" row.

### Decision

Model durable notification jobs (one logical send target) and notification attempts (execution history). Job carries current status and scheduling fields; attempts are append-only history.

### Consequences

- Pros:
  - Clean separation of current state vs execution history.
  - Supports retry-failures and operational analysis.
- Cons:
  - More tables/joins and write volume.

### Alternatives Rejected

- Single row with mutable retry columns only:
  - Rejected because it loses attempt history and weakens observability.

## ADR-006 - Deterministic Idempotency Key with Persistent Uniqueness Guard

- Status: Accepted

### Context

Crashes, retries, and lease failover can replay work. The platform must avoid duplicate sends.

### Decision

Compute idempotency key from logical send identity:

- `hash(tenantId + campaignId + recipientId + channel + normalizedMessageTemplate)`

Persist it in a uniqueness-constrained field/table used before provider simulation call.

### Consequences

- Pros:
  - Protects against duplicate sends across retries/restarts.
  - Works with partition failover in Part 4.
- Cons:
  - Requires stable message normalization and careful update rules for campaign content.

### Alternatives Rejected

- Random per-attempt idempotency key:
  - Rejected because it does not prevent duplicate logical sends after replay.
- In-memory dedupe only:
  - Rejected because it fails across process restarts and multiple workers.

## ADR-007 - Rule Engine as Ordered Policy Chain (Suppression -> Quiet Hours -> Allow)

- Status: Accepted

### Context

Business rules include global suppression, quiet-hours for SMS/Push (unless transactional), and future extensibility expectations.

### Decision

Implement a simple ordered rule engine that returns a decision object:

- `ALLOW`
- `SKIP` (reason = suppression/policy)
- `DELAY` (reason = quiet_hours, with `nextEligibleAt`)

### Consequences

- Pros:
  - Clear extension point for future tenant/channel rules.
  - Makes outcomes explicit and testable.
- Cons:
  - Requires a small abstraction layer before "just if-statements" implementation.

### Alternatives Rejected

- Inline conditional logic inside worker:
  - Rejected because rules become hard to test and extend.

## ADR-008 - Global Per-Channel Rate Limit (`100/min`) Shared Across Tenants

- Status: Accepted

### Context

Requirement states rate limiting `100 requests/min per channel` but does not specify tenant scope.

### Decision

Interpret the limit as a global provider budget per channel (`EMAIL`, `SMS`, `PUSH`) shared across all tenants. Throttling results in deferred scheduling/back-pressure, not synchronous blocking of the API.

### Consequences

- Pros:
  - Conservative and easy to defend.
  - Demonstrates realistic shared-provider constraints.
- Cons:
  - Fairness issues under noisy tenants (deferred to production enhancements).

### Alternatives Rejected

- Per-tenant rate limit only:
  - Rejected because it contradicts the stricter provider-capacity interpretation.

## ADR-009 - Extend Notification Statuses with `DELAYED` and `SKIPPED`

- Status: Accepted

### Context

Quiet-hours and suppression outcomes do not fit cleanly into success/failure-only workflows.

### Decision

Use explicit statuses for:

- `DELAYED` (quiet-hours or policy deferral)
- `SKIPPED` (suppression/policy exclusion)

### Consequences

- Pros:
  - Improves observability and reporting.
  - Simplifies retry-failures semantics (skip non-failure outcomes).
- Cons:
  - More transitions and counters to maintain.

### Alternatives Rejected

- Represent quiet-hours as retries:
  - Rejected because it hides policy behavior and inflates failure metrics.
- Represent suppression as failures:
  - Rejected because suppression is a valid policy outcome.

## ADR-010 - Part 4 Scaling via Partitioned DB Workers with Lease Ownership

- Status: Accepted

### Context

Part 4 requires a concrete scaling strategy with back-pressure and bottleneck analysis. The current design already uses DB-backed jobs.

### Decision

Scale by partitioning notification jobs (hash-based shard key) and introducing worker partition lease ownership. Each worker processes only owned partitions and respects per-channel throttling.

### Consequences

- Pros:
  - Incremental scaling path without redesigning core domain state.
  - Demonstrates partition ownership, failover, and throughput controls.
- Cons:
  - Lease management complexity and potential hot partitions.
  - DB remains central bottleneck at high scale.

### Alternatives Rejected

- Immediate Kafka migration for Part 4:
  - Rejected as higher implementation risk and infrastructure overhead for the take-home.
- Pure thread-count increase without partition ownership:
  - Rejected because it does not demonstrate coordinated multi-worker scaling.

## ADR-011 - Structured Logging with Correlation IDs and PII Masking

- Status: Accepted

### Context

The prompt requires structured logging and PII masking. Async workflows require correlation across API and worker paths.

### Decision

Use structured logs with consistent identifiers (`correlationId`, `tenantId`, `campaignId`, `notificationJobId`, `channel`, `attempt`) and mask email/phone/message content.

### Consequences

- Pros:
  - Strong debuggability for async flows.
  - Meets privacy expectations for an interview-ready design.
- Cons:
  - Requires disciplined logging wrappers/interceptors.

### Alternatives Rejected

- Free-form text logging:
  - Rejected because it weakens traceability and violates the prompt intent.

## Deferred ADRs (To Be Formalized Later if Implemented)

- Broker migration trigger criteria and CDC outbox publishing
- Object storage ingestion pipeline
- Tenant fairness scheduling and quotas
- Provider adapter resilience (circuit breakers/bulkheads)
- Tracing stack and SLO alert policy
