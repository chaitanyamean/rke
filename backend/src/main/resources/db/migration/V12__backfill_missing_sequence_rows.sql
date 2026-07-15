-- =============================================================================
-- V12 — Backfill missing sequence rows for existing tenants / categories.
--
-- TenantService.create() now provisions these rows automatically, but any tenant
-- created before this fix (e.g. via the Super Admin UI before V12) has no rows
-- in cotton_lot_sequences or transaction_no_sequences. This migration inserts the
-- missing rows so those tenants can start creating transactions immediately.
--
-- bill_number_sequences: one row per (tenant, item_category) — inserts for all
-- existing (tenant, category) combinations that have no sequence row yet.
-- cotton_lot_sequences:  one row per tenant — inserts for all tenants that have none.
-- transaction_no_sequences: one row per tenant — same.
--
-- All inserts are ON CONFLICT DO NOTHING so this is safe to run against
-- databases that already have some rows (e.g. RK Enterprises seeded in V2/V5/V10).
-- =============================================================================

-- bill_number_sequences: one per (tenant, item_category) pair that is missing one.
INSERT INTO bill_number_sequences (tenant_id, item_category_id, current_sequence, prefix, padding_width, format_template)
SELECT ic.tenant_id, ic.id, 0, '', 6, '{PREFIX}{SEQ}'
FROM item_categories ic
WHERE NOT EXISTS (
    SELECT 1 FROM bill_number_sequences bs
    WHERE bs.tenant_id = ic.tenant_id AND bs.item_category_id = ic.id
)
ON CONFLICT ON CONSTRAINT uq_bill_number_sequences DO NOTHING;

-- cotton_lot_sequences: one per tenant that is missing one.
INSERT INTO cotton_lot_sequences (tenant_id, current_sequence, prefix, padding_width, format_template)
SELECT t.id, 0, 'CTNL', 4, '{PREFIX}-{SEQ}'
FROM tenants t
WHERE NOT EXISTS (
    SELECT 1 FROM cotton_lot_sequences cs WHERE cs.tenant_id = t.id
)
ON CONFLICT ON CONSTRAINT uq_cotton_lot_sequences_tenant DO NOTHING;

-- transaction_no_sequences: one per tenant that is missing one.
INSERT INTO transaction_no_sequences (tenant_id, current_sequence)
SELECT t.id, 0
FROM tenants t
WHERE NOT EXISTS (
    SELECT 1 FROM transaction_no_sequences ts WHERE ts.tenant_id = t.id
)
ON CONFLICT ON CONSTRAINT uq_transaction_no_sequences_tenant DO NOTHING;
