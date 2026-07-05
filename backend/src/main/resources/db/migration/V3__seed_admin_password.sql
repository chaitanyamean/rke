-- =============================================================================
-- V3 — Set a usable bcrypt password on the seeded RK Enterprises admin so it can
-- be used for local testing / login.
--
--   username : admin   (tenant: RK Enterprises)
--   password : admin123
--
-- The hash below is bcrypt ($2b$, cost 10) of "admin123", verified against
-- Spring Security's BCryptPasswordEncoder.
--
-- NOTE: this is a LOCAL/DEV convenience credential only. For any real
-- environment, rotate this immediately (or seed via a secret) — do not ship
-- "admin123" to production.
-- =============================================================================

UPDATE staff_users
   SET password_hash = '$2b$10$7JuZbJinOLdaLlI7e130pezbFQIvpiMbak4VsHh4qOVhy6L.9yHG.'
 WHERE id = '22222222-2222-2222-2222-222222222222';
