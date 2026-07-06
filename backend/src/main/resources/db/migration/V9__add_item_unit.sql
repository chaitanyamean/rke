-- =============================================================================
-- V9 — Add a unit of measure to items (e.g. Bag, Bottle, Pkt).
--
-- Existing rows are backfilled to 'Bag' since that's the most common unit
-- today; the CHECK constraint follows this schema's convention for small,
-- closed enums (see ck_transactions_type, ck_staff_users_role). Extending the
-- allowed set later just needs a new migration to alter the constraint.
-- =============================================================================

ALTER TABLE items ADD COLUMN unit text NOT NULL DEFAULT 'Bag';
ALTER TABLE items ALTER COLUMN unit DROP DEFAULT;
ALTER TABLE items ADD CONSTRAINT ck_items_unit CHECK (unit IN ('Bag', 'Bottle', 'Pkt'));
