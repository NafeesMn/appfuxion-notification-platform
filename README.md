# Notification Platform Backend (Assessment)

## Overview

Backend take-home assessment project for a multi-tenant notification and campaign platform supporting simulated Email/SMS/Push delivery with asynchronous processing.
You may view the simple frontend app deployed [Here](https://notification-platform-frontend.vercel.app/campaigns)

## Assessment Scope (Planned)

Core requirements to implement in later phases:

- `POST /campaigns` with CSV upload (streamed row-by-row)
- `GET /campaigns`
- `GET /campaigns/{id}`
- `POST /campaigns/{id}/retry-failures`
- Async processing (202 Accepted, no synchronous send)
- Retry with exponential backoff
- Idempotency guarantees
- Global per-channel rate limiting (`100/min`)
- Quiet-hours + suppression business rules
- Tenant isolation
- Structured logging with correlation IDs and PII masking

## Chosen Architecture

- Java Spring Boot (modular monolith)
- PostgreSQL as system of record
- Flyway migrations
- Transactional outbox pattern for async workflow signaling
- DB-backed worker processing (initial)
- Scaling: partitioned DB-backed workers with shard ownership

## Repository Status

- Spring Boot + PostgreSQL + Flyway baseline implemented
- Campaign create/query/retry APIs implemented
- Scaling runtime (partitioned worker coordination) implemented
- Planning artifacts maintained under `docs/assessment/`

## Planned Architecture (High Level)

The backend will run as a modular monolith with internal bounded contexts:

- Tenant Governance
- Campaign Management
- Audience Ingestion (streaming CSV)
- Delivery Orchestration
- Rule Engine
- Outbox/Worker Processing
- Observability (structured logs + metrics)

State will be stored in PostgreSQL with durable notification jobs, attempts, and outbox events. Workers will poll due work and apply rules, throttling, retries, and idempotency before calling provider simulators.

## API Endpoints

- `POST /campaigns`
- `GET /campaigns`
- `GET /campaigns/{id}`
- `POST /campaigns/{id}/retry-failures`

Detailed request/response contracts will be added in implementation phases.

### Prerequisites

- Java
- Maven Wrapper (`./mvnw` / `mvnw.cmd`)
- PostgreSQL (local or container)

### Environment Variables

Planned examples (final names may change):

- `SPRING_DATASOURCE_URL`
- `SPRING_DATASOURCE_USERNAME`
- `SPRING_DATASOURCE_PASSWORD`
- `APP_TENANT_HEADER_NAME`
- `APP_WORKER_ENABLED`
- `APP_PROVIDER_SIMULATOR_SEED`
- `APP_SCALING_WORKER_ENABLED`
- `APP_SCALING_WORKER_ID`
- `APP_SCALING_ACTIVE_WORKERS`
- `APP_SCALING_TOTAL_PARTITIONS`

## Testing

Planned test strategy:

- Unit tests for rules, retries, idempotency normalization
- Integration tests with PostgreSQL (Testcontainers preferred)
- Load/benchmark script for Part 4 scaling demonstration

Run all tests:

```powershell
.\mvnw.cmd test
```

Run scaling benchmark harness:

```powershell
.\scripts\phase10\run-scaling-benchmark.ps1
```

## Notes

- This project intentionally starts with a DB-backed async design to reduce infrastructure overhead while preserving a migration path to Kafka/RabbitMQ later.
- Production-hardening topics (auth, circuit breakers, distributed tracing, secret management) are documented in `ENGINEERING_NOTES.md` and will be discussed in the final system design phase.
