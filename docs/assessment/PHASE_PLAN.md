# Phase Plan (Phase 0 to Phase 11)

## Summary

This roadmap converts the assessment requirements into an implementation sequence that preserves the Phase 0 architecture decisions and reduces rework. It is backend-focused and intentionally keeps the system as a modular monolith through the assessment.

## Guiding Principles

- Implement correctness and recoverability before throughput optimization.
- Keep schema and state transitions explicit before adding worker parallelism.
- Build observability with each phase instead of treating it as a final add-on.
- Defer broker migration; preserve extensibility via outbox/event boundaries.

## Phases

### Phase 0 - Scope, Architecture, Assumptions (Current)

Goals:

- Lock architecture decisions and tradeoffs.
- Define bounded contexts, conceptual domain model, async flow, and assumptions.
- Produce `ENGINEERING_NOTES.md`, assessment docs, diagram, and README skeleton.

Deliverables:

- Planning docs under `docs/assessment/`
- Root `ENGINEERING_NOTES.md`
- Updated `README.md`

### Phase 1 - Domain Schema Design and Migration Baseline

Goals:

- Translate conceptual domain model into concrete schema and Flyway migrations.
- Define enums/statuses and persistence boundaries for campaign, recipients, jobs, attempts, outbox.

Deliverables:

- Flyway baseline migrations
- Domain enums/value objects
- JPA entities (or persistence mappings) aligned to aggregates

Acceptance:

- Schema supports async polling, retries, idempotency uniqueness, tenant scoping, and reporting queries.

### Phase 2 - API Contracts and Validation Layer

Goals:

- Define request/response contracts for Part 3 endpoints.
- Implement validation, error envelope, and tenant context propagation.

Deliverables:

- DTOs and validation rules
- Exception handling and consistent API error responses
- Controller skeletons returning placeholder or delegated responses

Acceptance:

- Endpoint signatures are stable and aligned with Phase 0 documents.

### Phase 3 - Campaign Creation with Streaming CSV Ingestion

Goals:

- Implement `POST /campaigns` request flow (metadata + CSV upload).
- Stream CSV row-by-row, validate, normalize, persist recipients.
- Create campaign and enqueue async processing signal.

Deliverables:

- CSV parser/ingestion service
- Campaign persistence and import summary
- Transactional outbox insert on campaign dispatch request

Acceptance:

- Returns `202 Accepted`.
- Does not load full CSV in memory.
- Stores recipients and outbox event reliably.

### Phase 4 - Worker Execution Core (Single-Instance Baseline)

Goals:

- Implement DB-backed worker polling and due-job processing.
- Integrate provider simulator adapter.
- Implement notification state transitions and attempt recording.

Deliverables:

- Worker poll/claim loop
- Provider simulator integration
- Attempt persistence + job updates

Acceptance:

- Async delivery works end-to-end with observable status changes.

### Phase 5 - Rule Engine (Suppression + Quiet Hours)

Goals:

- Implement simple ordered rule engine.
- Add suppression skip and quiet-hours delay behavior.
- Capture rule decisions in logs and status transitions.

Deliverables:

- Rule evaluator chain
- Suppression lookup integration
- Quiet-hours scheduler deferral logic

Acceptance:

- SMS/Push quiet-hours enforcement works with timezone fallback.
- Suppressed recipients are `SKIPPED`, not `FAILED`.

### Phase 6 - Retry, Backoff, Idempotency, and Throttling

Goals:

- Implement exponential backoff + max retries.
- Enforce deterministic idempotency key persistence guard.
- Add global per-channel rate limiting (`100/min`) with back-pressure behavior.

Deliverables:

- Retry policy implementation
- Idempotency guard integration
- Channel throttling component

Acceptance:

- Safe reprocessing after restart does not duplicate sends.
- Throttled sends are deferred without hot-looping.

### Phase 7 - Query APIs and Retry-Failures Command

Goals:

- Implement:
  - `GET /campaigns`
  - `GET /campaigns/{id}`
  - `POST /campaigns/{id}/retry-failures`
- Ensure tenant-scoped filtering and status summaries.

Deliverables:

- Read models / query services
- Retry-failures rescheduling flow

Acceptance:

- Failed notifications can be rescheduled without duplicating already sent/skipped ones.

### Phase 8 - Structured Logging, Metrics, and Operational Visibility

Goals:

- Standardize structured logs and PII masking.
- Add operational metrics for queues, retries, outcomes, and throttling.

Deliverables:

- Logging interceptors/context propagation
- Metrics instrumentation (Actuator/Micrometer-based)
- Correlation id propagation across API and worker paths

Acceptance:

- Logs contain traceable identifiers and mask PII consistently.

### Phase 9 - Test Suite (Unit + Integration)

Goals:

- Add unit coverage for rules, retries, idempotency, and normalization.
- Add integration tests (Testcontainers PostgreSQL preferred) for critical flows.

Deliverables:

- Unit tests
- Integration tests for campaign create, worker processing, retries, tenant isolation

Acceptance:

- Core Part 3 behaviors are regression-tested and deterministic.

### Phase 10 - Part 4 High Throughput & Scaling Implementation

Goals:

- Implement partitioned DB-backed workers with shard ownership leases.
- Demonstrate back-pressure, throttling behavior, and benchmark/load results.

Deliverables:

- Partition lease model and worker ownership logic
- Load/benchmark script
- Bottleneck analysis notes and measured results

Acceptance:

- Multi-worker processing scales beyond baseline single-worker throughput.
- Partition failover works without duplicate sends (idempotency validated).

### Phase 11 - Part 5 System Design Package and Final Hardening Notes

Goals:

- Produce final system design narrative for 10M/day scale and multi-tenant growth.
- Document outage behavior, DB scaling/partitioning, retention, security, and observability architecture.
- Reconcile actual implementation limitations vs production recommendations.

Deliverables:

- System design document/section updates
- Final `ENGINEERING_NOTES.md` refinements
- Submission-ready README and architecture references

Acceptance:

- Design narrative is consistent with implemented architecture and explicit about tradeoffs/deferred components.

## Cross-Phase Dependencies

- Phase 1 schema decisions must preserve fields for Part 4 partitioning (`partitionKey`, lease-friendly indexes) even if unused initially.
- Phase 3 ingestion must persist enough metadata for Part 7 summaries and Part 8 observability.
- Phase 6 idempotency and throttling are prerequisites for credible Part 10 scale tests.

## Definition of Phase 1 Readiness (From Phase 0)

Phase 1 can begin when:

- Bounded contexts and state models are locked.
- Assumptions are documented and accepted.
- Async flow and outbox semantics are unambiguous.
- Schema design targets are clear enough to implement without revisiting architecture.
