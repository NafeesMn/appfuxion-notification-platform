-- Seed default tenants for local/assessment testing.
-- Uses deterministic UUIDs and idempotent inserts keyed by tenant_key.

INSERT INTO tenants (id, tenant_key, name, default_timezone, status)
SELECT
    '11111111-1111-1111-1111-111111111111'::uuid,
    'tenant-1',
    'Tenant One',
    'Asia/Kuala_Lumpur',
    'ACTIVE'
WHERE NOT EXISTS (
    SELECT 1 FROM tenants WHERE tenant_key = 'tenant-1'
);

INSERT INTO tenants (id, tenant_key, name, default_timezone, status)
SELECT
    '22222222-2222-2222-2222-222222222222'::uuid,
    'tenant-2',
    'Tenant Two',
    'UTC',
    'ACTIVE'
WHERE NOT EXISTS (
    SELECT 1 FROM tenants WHERE tenant_key = 'tenant-2'
);
