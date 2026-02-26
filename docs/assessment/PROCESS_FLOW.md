# End-to-End Process Flow (Async + Outbox + Worker)

## Summary

This document describes the high-level runtime flow from campaign ingestion through delivery, retries, rate limiting, and reprocessing. It is intentionally implementation-ready but avoids code-level detail.

## Core Design Guarantees

- `POST /campaigns` returns `202 Accepted` and does not perform synchronous provider sends.
- CSV is processed in streaming mode (row-by-row) to avoid full-file memory load.
- Domain state changes and async dispatch signals are written transactionally (outbox pattern).
- Worker processing is crash-safe via durable job state + idempotency guard.
- All domain data and queries are tenant-scoped.

## Flow 1 - `POST /campaigns` (Campaign Create + CSV Upload)

### Inputs

- Tenant context (header or request field in initial implementation)
- Campaign metadata:
  - channel (`EMAIL`, `SMS`, `PUSH`)
  - campaign type (`TRANSACTIONAL` or non-transactional)
  - message/template content
- CSV file upload

### High-Level Steps

1. Request enters API layer.
2. Generate or propagate `correlationId`.
3. Resolve tenant and load tenant defaults (including `default_timezone`).
4. Validate request metadata (channel/type/content/multipart format).
5. Create campaign aggregate in an "accepted/ingesting" state.
6. Stream CSV rows sequentially:
   - parse row
   - validate recipient fields for the chosen channel
   - normalize recipient identity (email/phone/token)
   - resolve timezone (`row.timezone` or `tenant.default_timezone`)
   - persist accepted recipient row
   - record row-level rejection for invalid rows (non-fatal unless file-level corruption)
7. Create notification jobs (one per accepted recipient, same channel in Phase 3 scope).
8. Insert outbox event `CampaignDispatchRequested` in the same transaction scope as final campaign readiness update.
9. Return `202 Accepted` with campaign id and import summary.

### Transaction Boundary Notes

- The design supports either:
  - one transaction for modest CSV files, or
  - chunked transactions for large files plus explicit import state
- Phase 1 will choose the physical strategy, but the architecture does not depend on that choice.

## Flow 2 - Async Dispatch Scheduling (Outbox / Due-Work Polling)

### Trigger

- Worker loop polls:
  - outbox events requiring action, and/or
  - notification jobs due for processing (`nextAttemptAt <= now`)

### Steps

1. Worker claims a batch of outbox events or due jobs (single-worker baseline initially).
2. If consuming outbox dispatch event:
   - mark campaign/jobs ready for processing (if not already done)
   - emit/log transition
3. Query due notification jobs with bounded batch size and tenant-safe filters.
4. Claim jobs (row locking / status transition / lease semantics, exact mechanism decided in implementation).

### Design Intent

- Keep worker claim semantics explicit to avoid duplicate concurrent processing.
- Phase 4 extends this with partition ownership and leases.

## Flow 3 - Rule Evaluation Before Provider Send

### Rule Order (Initial)

1. Global suppression rule
2. Quiet-hours rule for SMS/Push (bypass for transactional campaigns)

### Outcomes

- `ALLOW`
  - continue to throttling and send
- `SKIP` (suppression)
  - set notification status to `SKIPPED`
  - record reason and update campaign counters
  - no retry
- `DELAY` (quiet-hours)
  - compute next eligible local-time send window
  - set notification status to `DELAYED`
  - set `nextAttemptAt`
  - no failure count increment

### Important Behavior

- Quiet-hours deferral is not treated as retry/failure.
- Rule decisions should be logged with masked identifiers and reason codes.

## Flow 4 - Rate Limiting and Back-Pressure (Global Per Channel)

### Constraint

- Max `100 requests/min` per channel globally across all tenants.

### Worker Behavior

1. Before provider call, check channel budget availability.
2. If budget available:
   - reserve/consume a slot and continue.
3. If budget exhausted:
   - defer job by setting near-future `nextAttemptAt`
   - keep status as `DELAYED` (rate policy) or `RETRY_SCHEDULED` equivalent (final naming to be locked in schema)
   - log throttling event

### Back-Pressure Principles

- Do not spin in a tight retry loop when throttled.
- Use bounded polling batches to avoid overwhelming the DB.
- Keep API ingestion decoupled from immediate send capacity.

## Flow 5 - Provider Simulation and Attempt Recording

### Steps

1. Compute/check idempotency key (deterministic logical send identity).
2. Enforce persistent uniqueness guard before issuing provider request.
3. Call provider simulator adapter (Email/SMS/Push simulation).
4. Record `NotificationAttempt` with outcome and timing.
5. Transition `NotificationJob`:
   - success -> `SENT`
   - retryable failure -> `RETRY_SCHEDULED` with exponential backoff
   - terminal failure -> `FAILED`
6. Update campaign counters/progress summary.
7. Emit outbox event(s) for significant state changes (optional internal use, useful for future externalization).

### Idempotency Guarantee Intent

- If a worker crashes and reprocesses the same job, the dedupe guard prevents duplicate provider sends for the same logical notification.

## Flow 6 - Retry Processing (Exponential Backoff)

### Retryable Failure Path

1. Provider simulator returns retryable error (e.g., temporary failure / timeout simulation).
2. Worker increments attempt count.
3. Compute next delay using exponential backoff policy:
   - `nextDelay = baseDelay * multiplier^(attempt-1)` (capped)
4. Persist `nextAttemptAt` and status `RETRY_SCHEDULED`.
5. Poller picks the job again when due.

### Terminal Failure Path

- After `maxRetries` is exceeded (or terminal error code returned), mark `FAILED`.

## Flow 7 - `POST /campaigns/{id}/retry-failures`

### Intent

- Requeue only notifications that are currently terminal failures and eligible for retry by policy.

### Steps

1. Validate tenant-scoped campaign existence.
2. Identify failed notification jobs for that campaign.
3. Reset scheduling fields/status for retry (without changing sent/skipped jobs).
4. Insert outbox event `CampaignRetryFailuresRequested`.
5. Return accepted response with counts.

### Safety Constraints

- Must not requeue `SENT` or `SKIPPED` records.
- Idempotency still protects against accidental duplicate execution.

## Flow 8 - Query APIs (`GET /campaigns`, `GET /campaigns/{id}`)

### `GET /campaigns`

- Tenant-scoped paginated list of campaigns with status and summary counters.

### `GET /campaigns/{id}`

- Tenant-scoped campaign details including import summary and delivery summary.

### Read Consistency

- Eventual consistency is acceptable for derived counters during active processing.
- Aggregate state should remain durable and queryable without joining raw attempts for every request (implementation may use denormalized counters).

## Failure and Recovery Scenarios (Operational)

### Worker Crash Mid-Batch

- Unfinished jobs become eligible again after claim timeout / status recovery.
- Idempotency prevents duplicate sends.

### Database Temporary Outage

- API requests fail fast (or time out) without in-memory queueing.
- Worker processing pauses and resumes when DB returns.
- No work loss for already committed jobs/outbox events.

### Provider Simulator Outage

- Failures become retryable/terminal per policy.
- Backoff prevents immediate retry storms.

### Large Campaign Under Rate Limit Pressure

- Jobs accumulate in due queue but remain scheduled.
- Throughput is bounded by channel budget, not API thread speed.

## Phase 4 Extension - Partitioned Worker Processing

This flow extends the same model with partition ownership:

1. Notification jobs carry `partitionKey`.
2. Workers claim `WorkerPartitionLease` rows.
3. Each worker polls only owned partitions.
4. Lease heartbeat maintains ownership; expiry allows failover.
5. Global per-channel throttling remains enforced across partitions.

This preserves Part 3 behavior while demonstrating scalable coordination and back-pressure.
