# Notification Platform Backend (Assessment)

## Overview

Backend take-home assessment project for a multi-tenant notification and campaign platform supporting simulated Email/SMS/Push delivery with asynchronous processing.

This repository is currently in **Phase 0 (Architecture & Planning)**. The implementation code is intentionally minimal while the architecture, assumptions, and roadmap are being locked.

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

## Chosen Architecture (Phase 0)

- Java Spring Boot (modular monolith)
- PostgreSQL as system of record
- Flyway migrations
- Transactional outbox pattern for async workflow signaling
- DB-backed worker processing (initial)
- Phase 4 scaling: partitioned DB-backed workers with shard ownership

## Repository Status

- Existing Spring Boot scaffold generated
- Planning artifacts added under `docs/assessment/`
- Implementation of APIs, workers, and migrations is intentionally deferred to later phases

## Planning Documents

- `ENGINEERING_NOTES.md`
- `docs/assessment/PHASE_PLAN.md`
- `docs/assessment/ARCHITECTURE_DECISIONS.md`
- `docs/assessment/ASSUMPTIONS.md`
- `docs/assessment/CONCEPTUAL_DOMAIN_MODEL.md`
- `docs/assessment/PROCESS_FLOW.md`
- `docs/assessment/ARCHITECTURE_DIAGRAM.md`

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

## API Endpoints (Planned)

- `POST /campaigns`
- `GET /campaigns`
- `GET /campaigns/{id}`
- `POST /campaigns/{id}/retry-failures`

Detailed request/response contracts will be added in implementation phases.

## Setup (Placeholder - To Be Completed in Later Phase)

### Prerequisites

- Java (version TBD, align with project `pom.xml`)
- Maven Wrapper (`./mvnw` / `mvnw.cmd`)
- PostgreSQL (local or container)
- Docker (for Testcontainers integration tests, later)

### Environment Variables (Placeholder)

Planned examples (final names may change):

- `SPRING_DATASOURCE_URL`
- `SPRING_DATASOURCE_USERNAME`
- `SPRING_DATASOURCE_PASSWORD`
- `APP_TENANT_HEADER_NAME`
- `APP_WORKER_ENABLED`
- `APP_PROVIDER_SIMULATOR_SEED`

### Run Instructions (Placeholder)

Planned commands (to be finalized after implementation):

```powershell
.\mvnw.cmd spring-boot:run
```

### Database Migrations (Placeholder)

Flyway migrations will be added in Phase 1.

## Testing (Placeholder - To Be Completed Later)

Planned test strategy:

- Unit tests for rules, retries, idempotency normalization
- Integration tests with PostgreSQL (Testcontainers preferred)
- Load/benchmark script for Part 4 scaling demonstration

## Roadmap

See `docs/assessment/PHASE_PLAN.md` for the Phase 0-11 backend roadmap and acceptance milestones.

## Notes

- This project intentionally starts with a DB-backed async design to reduce infrastructure overhead while preserving a migration path to Kafka/RabbitMQ later.
- Production-hardening topics (auth, circuit breakers, distributed tracing, secret management) are documented in `ENGINEERING_NOTES.md` and will be discussed in the final system design phase.
