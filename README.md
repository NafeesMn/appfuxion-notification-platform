# Notification Platform Backend

## View Demo
[Demo](https://notification-platform-frontend.vercel.app)

## Prerequisites

- Java 21
- PostgreSQL
- Maven Wrapper (`mvnw` / `mvnw.cmd`)

## Setup

1. Create a PostgreSQL database:

```sql
CREATE DATABASE notification_platform_backend;
```

2. Override database connection defaults.
   Default values are:
   - host: `localhost`
   - port: `5432`
   - database: `notification_platform_backend`
   - username: `postgres`
   - password: `postgres`

   Environment variables:
   - `DB_HOST`
   - `DB_PORT`
   - `DB_NAME`
   - `DB_USERNAME`
   - `DB_PASSWORD`

3. (Optional) Configure runtime settings:
   - `APP_TENANT_HEADER_NAME` (default: `X-Tenant-Key`)
   - `APP_CORRELATION_HEADER_NAME` (default: `X-Correlation-Id`)
   - `APP_SCALING_WORKER_ENABLED` (default: `true`)
   - `APP_SCALING_WORKER_ID` (default: `worker-local`)
   - `APP_SCALING_ACTIVE_WORKERS` (default: `1`)
   - `APP_SCALING_TOTAL_PARTITIONS` (default: `128`)

## Run

Windows:

```powershell
.\mvnw.cmd spring-boot:run
```

macOS/Linux:

```bash
./mvnw spring-boot:run
```

The app starts on `http://localhost:8080` by default.
Flyway migrations run automatically at startup.

## Verify

Check health endpoint:

```bash
curl http://localhost:8080/actuator/health
```
