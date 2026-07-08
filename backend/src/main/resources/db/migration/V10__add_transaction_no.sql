-- =============================================================================
-- V10 — Human-readable transaction number, distinct from the internal uuid id.
--
-- Format: {YYYY}-{billNumber}-{increment}
--   YYYY      = calendar year the transaction is created (from transaction_date's
--               year would drift for back-dated entries, so this uses the actual
--               creation year instead — see next_transaction_no()).
--   billNumber = the transaction's own bill_number (already composed on the
--               frontend as "{BillNumberType.name}-{number}", e.g. "CS-0001").
--   increment  = a simple per-tenant running counter that only exists to
--               guarantee uniqueness if the same bill number is ever reused
--               (e.g. across categories/tenants edge cases) — it does NOT reset
--               per year or per bill number.
--
-- Example: bill_number "CS-0001" in 2026, 1st transaction row overall for the
-- tenant -> "2026-CS-0001-1".
--
-- transaction_no is unique per tenant, nullable only for legacy rows created
-- before this migration (see backfill below).
-- =============================================================================

ALTER TABLE transactions ADD COLUMN transaction_no text;

CREATE TABLE transaction_no_sequences (
    id               uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id        uuid NOT NULL,
    current_sequence bigint NOT NULL DEFAULT 0,
    created_at       timestamptz NOT NULL DEFAULT now(),
    updated_at       timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT fk_transaction_no_sequences_tenant FOREIGN KEY (tenant_id)
        REFERENCES tenants (id) ON DELETE RESTRICT,
    CONSTRAINT uq_transaction_no_sequences_tenant UNIQUE (tenant_id),
    CONSTRAINT ck_transaction_no_sequences_seq_nonneg CHECK (current_sequence >= 0)
);
CREATE INDEX idx_transaction_no_sequences_tenant ON transaction_no_sequences (tenant_id);
CREATE TRIGGER trg_transaction_no_sequences_updated_at BEFORE UPDATE ON transaction_no_sequences
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- Seed for RK Enterprises (mirrors the cotton_lot_sequences / bill_number_sequences seeding pattern).
INSERT INTO transaction_no_sequences (tenant_id, current_sequence)
VALUES ('11111111-1111-1111-1111-111111111111', 0);

-- Atomically increments the per-tenant counter and returns the formatted
-- transaction number. Concurrency-safe via UPDATE ... RETURNING row lock (same
-- pattern as next_bill_number / next_cotton_lot_serial).
CREATE OR REPLACE FUNCTION next_transaction_no(p_tenant_id uuid, p_bill_number text)
RETURNS text
LANGUAGE plpgsql
AS $$
DECLARE
    v_seq bigint;
BEGIN
    UPDATE transaction_no_sequences
       SET current_sequence = current_sequence + 1
     WHERE tenant_id = p_tenant_id
    RETURNING current_sequence INTO v_seq;

    IF NOT FOUND THEN
        RAISE EXCEPTION 'No transaction_no_sequence configured for tenant %', p_tenant_id;
    END IF;

    RETURN extract(year FROM now())::text || '-' || p_bill_number || '-' || v_seq::text;
END;
$$;

-- Backfill existing rows so transaction_no is never null going forward for them
-- too (uses each row's own created_at year, consistent with "creation year").
DO $$
DECLARE
    r RECORD;
    v_seq bigint;
BEGIN
    FOR r IN SELECT id, tenant_id, bill_number, created_at FROM transactions ORDER BY created_at LOOP
        UPDATE transaction_no_sequences
           SET current_sequence = current_sequence + 1
         WHERE tenant_id = r.tenant_id
        RETURNING current_sequence INTO v_seq;

        UPDATE transactions
           SET transaction_no = extract(year FROM r.created_at)::text || '-' || r.bill_number || '-' || v_seq::text
         WHERE id = r.id;
    END LOOP;
END $$;

ALTER TABLE transactions ALTER COLUMN transaction_no SET NOT NULL;
ALTER TABLE transactions ADD CONSTRAINT uq_transactions_tenant_transaction_no UNIQUE (tenant_id, transaction_no);
