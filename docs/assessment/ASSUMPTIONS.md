# Assumptions (Locked for Phase 0)

## Summary

The assessment prompt leaves several behaviors intentionally ambiguous. This file locks the assumptions used to implement the system so the design stays consistent and interview-defensible.

These assumptions are defaults for the assessment implementation and can be changed later if a requirement clarifies otherwise.

## Locked Assumptions (Top 5)

### A1 - Recipient Timezone Ambiguity

- Topic: Recipient timezone for quiet-hours evaluation
- Decision:
  - CSV supports an optional `timezone` column.
  - If missing, use `tenant.default_timezone`.
- Reason:
  - Quiet-hours requires recipient-local time but CSV examples may not include timezone data.
- Impact on design:
  - Tenant configuration must store `default_timezone`.
  - Recipient rows should persist effective timezone or enough data to derive it deterministically.

### A2 - Rate Limit Scope

- Topic: Scope of `100 requests/min per channel`
- Decision:
  - Treat as a global provider limit per channel (`EMAIL`, `SMS`, `PUSH`) shared across all tenants.
- Reason:
  - Requirement specifies "per channel" without "per tenant"; provider-capacity interpretation is stricter and safer.
- Impact on design:
  - Throttling must be coordinated at the channel level.
  - Part 4 scaling must preserve global channel budgets across workers.

### A3 - Idempotency Key

- Topic: How duplicate sends are prevented across retries/restarts
- Decision:
  - `idempotencyKey = hash(tenantId + campaignId + recipientId + channel + normalizedMessageTemplate)`
- Reason:
  - Protects against replay while allowing different campaigns/messages to send independently.
- Impact on design:
  - Message normalization rules must be deterministic.
  - Schema must support uniqueness enforcement on logical send identity.

### A4 - Notification Status Model

- Topic: Status values needed for business rules
- Decision:
  - Extend base statuses with `DELAYED` and `SKIPPED`.
- Reason:
  - Quiet-hours and suppression are valid outcomes, not failures.
- Impact on design:
  - Query APIs and counters must distinguish failed vs skipped vs delayed.
  - Retry-failures endpoint only targets retryable/failed notifications.

### A5 - Part 4 Scaling Direction

- Topic: Scaling strategy for high-throughput implementation
- Decision:
  - Use partitioned DB-backed worker processing with partition/shard ownership.
- Reason:
  - Preserves architecture continuity from Part 3 while demonstrating back-pressure and throughput control.
- Impact on design:
  - Phase 1 schema should reserve fields/indexes that support partition key and due-work polling.

## Additional Working Assumptions (Supportive, Not Part of the Top 5)

### A6 - Campaign Content Immutability After Acceptance

- Decision:
  - Campaign message/template becomes immutable once async processing is enqueued.
- Reason:
  - Idempotency key includes normalized message template; mutation after enqueue would break dedupe semantics.

### A7 - `retry-failures` Scope

- Decision:
  - `POST /campaigns/{id}/retry-failures` requeues only terminal failed notifications that are retry-eligible by policy and not already sent/skipped.
- Reason:
  - Prevents accidental duplication and keeps command semantics narrow.

### A8 - Quiet-Hours Applicability

- Decision:
  - Quiet-hours applies only to SMS and Push, not Email.
  - Transactional campaigns bypass quiet-hours for SMS/Push.
- Reason:
  - Matches prompt wording and common notification domain expectations.

### A9 - CSV Row Validation Handling

- Decision:
  - Invalid rows are recorded as import errors/rejections (with counts) and do not block the entire campaign unless the file-level structure is invalid.
- Reason:
  - Supports resilient ingestion for real-world CSV quality while keeping campaign processing useful.

### A10 - Tenant Context Source (Assessment Scope)

- Decision:
  - Tenant identity will be provided via request header or simple request field in early implementation (auth deferred).
- Reason:
  - Tenant isolation is required; full authN/authZ is out of Phase 0/initial implementation scope.

## Ambiguities Explicitly Not Resolved Yet (Deferred)

- Exact CSV column names beyond required channel recipient fields and optional timezone
- Provider simulator failure distribution and configurability
- Whether campaign import and job expansion happen in a single transaction or chunked transactions for very large files

These are implementation details to finalize in Phase 1/2, but they do not change the architecture direction defined in Phase 0.

## Review Checklist (For Interview Discussion)

- Can each assumption be defended from the prompt wording or operational need?
- Does any assumption conflict with async + outbox + tenant isolation requirements?
- Does changing the assumption later require architecture changes or only implementation changes?

The current set is designed so most changes affect implementation details, not the overall architecture.
