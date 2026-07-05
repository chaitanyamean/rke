-- =============================================================================
-- V4 — Seed a platform super_admin login (cross-tenant, not scoped to any tenant).
--
--   username : superadmin
--   password : superadmin123
--
-- tenant_id is NULL (required for super_admin by the ck_staff_users_tenant_scope
-- constraint). The hash is bcrypt ($2b$, cost 10) of "superadmin123", verified
-- against Spring Security's BCryptPasswordEncoder.
--
-- NOTE: LOCAL/DEV convenience credential only — rotate before any real
-- environment. Do not ship "superadmin123" to production.
-- =============================================================================

INSERT INTO staff_users (id, tenant_id, username, password_hash, full_name, role, active)
VALUES (
    '55555555-5555-5555-5555-555555555555',
    NULL,
    'superadmin',
    '$2b$10$YamAhwnfYkz.eP3s0cedGeftnDzj8LRtcgCj9yhK5QIopJ/yc5lN.',
    'Platform Super Admin',
    'super_admin',
    true
);
