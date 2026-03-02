# Part 5 - System Design Package

## Scope

This document closes Part 5 for the backend assessment. It explains how the current implementation evolves to production scale (`10M notifications/day`) and where hardening is required.

Current implementation baseline:

- Spring Boot modular monolith
- PostgreSQL for core state + outbox
- DB-backed partitioned workers with lease ownership
- Rule engine (suppression + quiet hours)
- Retry/backoff + idempotency guard

## Scale Target and Traffic Model

Target:

- `10,000,000` notifications/day
- Average throughput: ~`116 msg/s` (`10,000,000 / 86,400`)
- Design peak target: `8x` to `12x` average (`900-1,400 msg/s`)

Assumptions used for sizing:

- 20k to 100k tenants, heavy skew across tenants
- 3 channels (EMAIL, SMS, PUSH), each with provider-specific budgets
- Campaign bursts are dominant load pattern (not uniform traffic)
- Notification attempts retained for audit/analytics (hot + cold tiers)

## High-Level Production Architecture

### Control Plane (API + Campaign Ingestion)

- API receives campaign metadata + CSV.
- CSV ingestion remains streaming; for very large files, move to object storage + async import job.
- Campaign and initial job rows are committed with outbox event in one transaction.

### Data Plane (Dispatch Workers)

- Worker fleet owns partitions via lease table.
- Workers fetch due jobs by owned partition + status + `next_attempt_at`.
- Rule engine executes before provider dispatch.
- Global rate limits enforce back-pressure and deferral.
- Attempt rows provide auditability and idempotency history.

### Eventing Boundary

- Transactional outbox remains the decoupling seam.
- Near-term option: DB poller publisher.
- Scale option: CDC or outbox relay to Kafka/RabbitMQ without changing campaign write path.

## Tenant Isolation and Fairness

Isolation currently:

- Logical isolation with tenant-scoped columns, filters, indexes, and request tenant header.

Production hardening:

- Add explicit authN/authZ with tenant claims.
- Add per-tenant quotas and fair-share scheduling.
- Add noisy-tenant containment:
  - max active campaigns per tenant
  - per-tenant in-flight job cap
  - priority queues for transactional traffic

## Database Scaling and Partitioning Plan

## 1) Vertical + Query/Index Hardening (immediate)

- Keep narrow hot queries:
  - `notification_jobs(status, next_attempt_at, partition_key)`
  - `campaigns(tenant_id, status, created_at)`
- Use batched claim/update paths and minimize row churn.

## 2) Table Partitioning (short term)

- `notification_attempts`: partition by time (monthly/daily based on volume).
- `outbox_events`: partition by time with aggressive retention.
- `notification_jobs`: consider hash partition by `partition_key` or tenant bucket when row count grows.

## 3) Read/Write Separation (medium term)

- Primary for writes and worker claims.
- Read replicas for dashboard/reporting queries (`GET /campaigns`, metrics views).
- Avoid dispatch logic against replicas.

## 4) Data Lifecycle and Retention

- `notification_attempts` hot retention: 30-90 days.
- Archive older attempts to cold storage (warehouse/object storage) for compliance.
- `outbox_events` retention: short hot window after publish + compaction.

## Outage and Failure Behavior

## Provider outage

- Retryable failures transition to `RETRY_SCHEDULED` with exponential backoff.
- Channel throttling and retry ceilings prevent hot loops.
- Degraded mode:
  - continue ingestion
  - defer dispatch
  - expose backlog metrics/alerts

## Worker/node crash

- Lease expiration enables takeover by healthy workers.
- Idempotency guard prevents duplicate logical sends after failover.

## Database partial outage

- API and worker both degrade because DB is system-of-record.
- Required production controls:
  - connection pool protection
  - read-only fail mode where possible
  - runbook-based failover and replay

## Observability Model

Minimum production signals:

- Queue depth by channel/tenant
- Due-job lag (`now - next_attempt_at`)
- Retry rate and retry exhaustion count
- Throttle deferrals by channel
- Worker lease churn and partition ownership balance
- Provider success/failure latency percentiles

Target SLOs (initial):

- Campaign ingestion acceptance latency p95
- Dispatch completion percentile per campaign size bucket
- Delivery success ratio by channel
- MTTD/MTTR for worker stall and provider degradation alerts

## Security Design

Required hardening beyond assessment:

- OAuth2/JWT auth with tenant-bound claims
- Role-based authorization for campaign actions
- Secret management for provider credentials (vault/KMS)
- Encryption:
  - in transit (TLS)
  - at rest (DB + backups)
- Audit trail for operator actions (`retry-failures`, policy changes)
- Strict PII controls:
  - masked logs
  - avoid raw body logging
  - retention policy for sensitive fields

## Evolution Path from Current Implementation

Phase A:

- Keep modular monolith + DB-backed workers, harden claim SQL and distributed limiter.

Phase B:

- Split out worker deployment profile and independently scale API vs workers.
- Introduce Redis/DB-backed global rate limiter.

Phase C:

- Introduce broker via outbox relay (Kafka/RabbitMQ).
- Use consumer groups for dispatch workers while preserving idempotency keys and attempt journal.

Phase D:

- Regionalization, DR, and compliance-grade archival.

## Current Gaps to Track

- No backend `/campaigns/{id}/metrics` endpoint yet (frontend fallback in place).
- Campaign aggregate status roll-up to terminal states is partial.
- Global channel limiter is currently local-process unless centralized backing is enabled.
- AuthN/AuthZ and provider credential management are deferred.

## Conclusion

The current architecture is a valid production-style foundation for the assessment goals. At `10M/day`, the first hard limits are DB write amplification and global coordination concerns (rate limits, fair scheduling). The recommended path is to preserve the current domain model/outbox boundary and evolve the transport and storage strategy incrementally, not through a full rewrite.
