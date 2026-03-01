# Phase 10 - High Throughput & Scaling

## Scope Completed

Phase 10 implementation focused on the required Part 4 outcomes:

- Partitioned DB-backed workers with shard ownership leases.
- Back-pressure and throttling behavior under worker execution.
- Benchmark/load harness and measurable throughput comparison.
- Bottleneck analysis and operational tradeoffs.

## Implemented Runtime Components

- `src/main/java/com/appfuxion_notification_platform/backend/config/ScalingWorkerConfiguration.java`
  - Wires partition planner, lease service, fetcher, executor, rule engine, retry policy, rate limiter, and coordinator.
- `src/main/java/com/appfuxion_notification_platform/backend/delivery/scaling/runtime/PartitionedWorkerLoop.java`
  - Scheduled poll loop (property-gated) for rebalance + execution + lease reclamation.
- `src/main/java/com/appfuxion_notification_platform/backend/delivery/scaling/runtime/DatabasePartitionLeaseService.java`
  - Lease acquire/heartbeat/release/reclaim using DB state and optimistic locking.
- `src/main/java/com/appfuxion_notification_platform/backend/delivery/scaling/runtime/DefaultPartitionedWorkerCoordinator.java`
  - Partition ownership orchestration, fetch/execute loop, heartbeats.
- `src/main/java/com/appfuxion_notification_platform/backend/delivery/scaling/runtime/RepositoryPartitionAwareJobFetcher.java`
  - Partition-aware due-job fetch and claim transition to `PROCESSING`.

## Runtime Controls

Configured in `src/main/resources/application.properties`:

- `app.scaling.worker.enabled`
- `app.scaling.worker-id`
- `app.scaling.active-workers`
- `app.scaling.total-partitions`
- `app.scaling.poll-batch-size`
- `app.scaling.poll-delay-ms`
- `app.scaling.lease-ttl-seconds`
- `app.scaling.rate-limit-per-minute`
- `app.scaling.retry.base-delay-seconds`
- `app.scaling.retry.multiplier`
- `app.scaling.retry.max-delay-seconds`

## Benchmark / Load Harness

Script:

- `scripts/phase10/run-scaling-benchmark.ps1`

Benchmark test:

- `src/test/java/com/appfuxion_notification_platform/backend/delivery/scaling/runtime/Phase10ScalingHarnessTest.java`

Command used on **March 1, 2026**:

```powershell
.\mvnw.cmd "-Dtest=Phase10ScalingHarnessTest" "-Drun.phase10.benchmark=true" "-Dphase10.jobs=4000" "-Dphase10.partitions=128" "-Dphase10.batch=100" "-Dphase10.workMicros=1000" test
```

Measured output:

- `oneWorker=682.59 jobs/s (5860 ms)`
- `fourWorkers=2710.03 jobs/s (1476 ms)`

Observed scale-up:

- ~`3.97x` throughput increase from 1 to 4 workers in the harness scenario.

## Bottleneck Analysis

### 1) Lease Table Contention

- Symptom: concurrent acquire attempts can contend on `worker_partition_leases`.
- Mitigation in this phase:
  - Optimistic locking handling in lease service.
  - Expired lease reclamation each poll cycle.
  - Index support from Phase 1 (`idx_worker_partition_leases_lease_expires_at`).

### 2) Partition Fetch Claim Path

- Symptom: claim transition (`PENDING`/`RETRY_SCHEDULED`/`DELAYED` -> `PROCESSING`) can become write-heavy.
- Mitigation in this phase:
  - Partition-scoped fetch to reduce cross-worker overlap.
  - Bounded `pollBatchSize`.
  - Poll cadence controls via `app.scaling.poll-delay-ms`.

### 3) Channel Throttling Throughput Ceiling

- Symptom: `100/min` per-channel budget caps delivery rate.
- Mitigation in this phase:
  - Back-pressure deferral to `DELAYED` with `next_attempt_at`.
  - Metric recording for throttle deferrals.

### 4) Current Limitation: Cross-Process Global Rate Limit

- Current `TokenBucketChannelRateLimiter` is in-memory per JVM.
- For true cross-instance global enforcement, migrate to DB/Redis-backed token coordination (deferred to hardening).

## Failover & Idempotency Behavior

- Failover path: expired partition leases are reclaimed and can be re-acquired by another worker.
- Duplicate-send protection:
  - Idempotency execution guard checks prior successful attempts.
  - Deterministic idempotency key model from earlier phases remains active.

## Test Coverage Added for Phase 10

- `src/test/java/com/appfuxion_notification_platform/backend/delivery/scaling/runtime/DatabasePartitionLeaseServiceTest.java`
- `src/test/java/com/appfuxion_notification_platform/backend/delivery/scaling/runtime/DefaultPartitionedWorkerCoordinatorTest.java`
- `src/test/java/com/appfuxion_notification_platform/backend/delivery/scaling/runtime/RepositoryPartitionAwareJobFetcherTest.java`
- `src/test/java/com/appfuxion_notification_platform/backend/delivery/scaling/runtime/HashPartitionPlannerTest.java`

## Deferred Production Enhancements

- Replace in-memory channel limiter with distributed limiter (Redis/DB token bucket).
- Use `FOR UPDATE SKIP LOCKED` claim SQL to further harden concurrent fetch claims.
- Add worker liveness registry for dynamic `activeWorkerCount` instead of static config.
- Add benchmark with real HTTP/API ingress + PostgreSQL dataset at larger scale.
