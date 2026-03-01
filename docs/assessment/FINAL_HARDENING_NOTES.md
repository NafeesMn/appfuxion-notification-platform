# Final Hardening Notes

## Intent

This note captures the production hardening backlog after finishing the assessment implementation path through Phase 11.

## P0 - Must Fix Before Production

1. Authentication and authorization
- Add tenant-bound auth tokens; remove trust in header-only tenant context.
- Enforce role-based permissions for create/list/detail/retry operations.

2. Global rate limiter centralization
- Replace in-memory channel rate limiter with shared backing (Redis or DB token bucket).
- Ensure correctness across multiple worker instances.

3. Worker claim and concurrency hardening
- Use robust claim SQL (`FOR UPDATE SKIP LOCKED` or equivalent) to reduce duplicate claim races.
- Add dead-letter behavior for poison jobs.

4. Metrics endpoint parity
- Implement backend metrics read model for campaign detail page.
- Remove frontend fallback once endpoint is stable.

5. Incident-grade observability
- Alerting for backlog growth, lease churn, retry exhaustion, provider failure spikes.
- Dashboards for API and worker health separation.

## P1 - High Priority (First 1-2 Sprints)

1. Campaign aggregate roll-up
- Maintain campaign terminal statuses (`COMPLETED`, `COMPLETED_WITH_FAILURES`) from job outcomes.
- Keep counters strongly consistent enough for operational reporting.

2. Data retention and archival
- Partition and age out `notification_attempts` and outbox history.
- Archive old attempts to cold storage.

3. Resilience controls
- Provider circuit breaker, timeout profiles, and bulkheads.
- Back-pressure safeguards at ingestion and dispatch.

4. Multi-tenant fairness controls
- Per-tenant dispatch quotas and burst caps.
- Guardrails for noisy tenants.

## P2 - Strategic Improvements

1. Broker adoption via outbox relay
- Keep write model intact; route outbox to Kafka/RabbitMQ.

2. Horizontal topology split
- Independent API and worker deployments with separate autoscaling signals.

3. Regional and DR strategy
- Backup verification, failover runbooks, and RTO/RPO targets.

4. Security/compliance depth
- Key rotation, field-level encryption for sensitive payload fragments, auditable access trails.

## Test and Verification Gaps

- Add integration tests for:
  - retry-failures repeated calls against previously attempted jobs
  - multi-worker contention under lease expiry
  - cross-instance limiter correctness
- Add fault-injection scenarios:
  - provider timeout storms
  - DB transient failures during claim/update

## Operational Runbooks Required

- Worker stuck/no-progress runbook
- Provider degraded mode runbook
- Tenant isolation incident response
- Retry storm mitigation runbook

## Release Readiness Exit Criteria

- P0 items complete
- SLO dashboards and paging verified
- Load test meets target throughput and error budget at projected peak
- Security review sign-off complete
