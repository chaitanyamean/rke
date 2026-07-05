-- =============================================================================
-- V6 — Composite indexes for report query performance.
--
-- The existing single-column indexes (tenant_id, farmer_id) are insufficient
-- for the multi-column filter patterns used in Phase 5 reports. These covering
-- indexes let Postgres satisfy the WHERE + ORDER BY without heap fetches.
-- =============================================================================

-- Farmer ledger: tenant + farmer + date (chronological per-farmer scan)
CREATE INDEX idx_transactions_tenant_farmer_date
    ON transactions (tenant_id, farmer_id, transaction_date);

-- Date-based reports: tenant + date + type (daily sales / payments breakdowns)
CREATE INDEX idx_transactions_tenant_date_type
    ON transactions (tenant_id, transaction_date, transaction_type);

-- Status-filtered scans: tenant + status + date (active-only date range queries)
CREATE INDEX idx_transactions_tenant_status_date
    ON transactions (tenant_id, status, transaction_date);

-- Item-level report join: transaction_id + item_id (already covered by existing
-- idx_transaction_items_transaction, but add item-first variant for item-centric queries)
CREATE INDEX idx_transaction_items_item_txn
    ON transaction_items (item_id, transaction_id);
