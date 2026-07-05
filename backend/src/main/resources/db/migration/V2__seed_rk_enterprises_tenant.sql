-- =============================================================================
-- V2 — Seed the first tenant: RK Enterprises.
--
-- The tenant UUID is HARDCODED to a fixed, known value (not gen_random_uuid())
-- so later phases, seed data, and tests can reference this tenant predictably:
--
--     RK Enterprises tenant_id = 11111111-1111-1111-1111-111111111111
--
-- logo_url / primary_color are intentionally left NULL — the branding upload
-- flow is Phase 2.5 and hasn't been built yet.
-- =============================================================================

INSERT INTO tenants (id, name, slug, logo_url, primary_color, active)
VALUES (
    '11111111-1111-1111-1111-111111111111',
    'RK Enterprises',
    'rk-enterprises',
    NULL,
    NULL,
    true
);

-- Admin login for RK Enterprises (role = admin, scoped to the tenant).
-- password_hash is a deliberately INVALID placeholder so this account cannot be
-- used to log in until a real credential is set through the admin flow. Do NOT
-- ship a real/default password here.
INSERT INTO staff_users (id, tenant_id, username, password_hash, full_name, role, active)
VALUES (
    '22222222-2222-2222-2222-222222222222',
    '11111111-1111-1111-1111-111111111111',
    'admin',
    'PLACEHOLDER::set-via-admin-flow-phase-2.5',
    'RK Enterprises Administrator',
    'admin',
    true
);

-- Enable the cotton procurement feature for RK Enterprises. Any feature_key not
-- present for a tenant is treated as disabled, so this row is what switches the
-- capability on for RK specifically.
INSERT INTO tenant_features (id, tenant_id, feature_key, enabled)
VALUES (
    '33333333-3333-3333-3333-333333333333',
    '11111111-1111-1111-1111-111111111111',
    'cotton_procurement',
    true
);
