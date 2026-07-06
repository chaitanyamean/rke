-- =============================================================================
-- V8 — Villages become global master data, shared across all tenants.
--
-- Previously villages were scoped per tenant (tenant_id NOT NULL, unique per
-- tenant+name). Any tenant's staff (or admin/super_admin) can now add a
-- village and it becomes visible to every other tenant, so villages are no
-- longer tenant-owned data.
--
-- Step 1 merges any villages that collide on name (case-insensitive) across
-- tenants, repointing farmers/cotton_lot_entries to a single surviving row
-- before dropping duplicates. Step 2 drops tenant scoping entirely.
-- =============================================================================

-- Step 1: merge duplicate village names, keeping the earliest-created row.
CREATE TEMP TABLE village_survivors AS
SELECT DISTINCT ON (lower(name)) lower(name) AS name_key, id AS keep_id
FROM villages
ORDER BY lower(name), created_at, id;

CREATE TEMP TABLE village_merge_map AS
SELECT v.id AS dupe_id, s.keep_id
FROM villages v
JOIN village_survivors s ON s.name_key = lower(v.name)
WHERE v.id <> s.keep_id;

UPDATE farmers f
SET village_id = m.keep_id
FROM village_merge_map m
WHERE f.village_id = m.dupe_id;

UPDATE cotton_lot_entries c
SET village_id = m.keep_id
FROM village_merge_map m
WHERE c.village_id = m.dupe_id;

DELETE FROM villages v
USING village_merge_map m
WHERE v.id = m.dupe_id;

DROP TABLE village_merge_map;
DROP TABLE village_survivors;

-- Step 2: drop tenant scoping, replace with a plain global unique name.
ALTER TABLE villages DROP CONSTRAINT fk_villages_tenant;
ALTER TABLE villages DROP CONSTRAINT uq_villages_tenant_name;
DROP INDEX IF EXISTS idx_villages_tenant;
ALTER TABLE villages DROP COLUMN tenant_id;
ALTER TABLE villages ADD CONSTRAINT uq_villages_name UNIQUE (name);
