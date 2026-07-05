-- =============================================================================
-- V5 — Cotton lot vehicle serial number auto-generation.
--
-- cotton_lot_sequences: one row per tenant, mirrors the pattern of
-- bill_number_sequences but scoped to cotton lots only (no item_category link).
--
-- next_cotton_lot_serial(tenant_id): atomically increments and returns a
-- formatted serial string — concurrency-safe via UPDATE ... RETURNING row lock.
-- =============================================================================

CREATE TABLE cotton_lot_sequences (
    id                uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id         uuid NOT NULL,
    current_sequence  bigint NOT NULL DEFAULT 0,
    prefix            text NOT NULL DEFAULT 'CTNL',
    padding_width     int NOT NULL DEFAULT 4,
    format_template   text NOT NULL DEFAULT '{PREFIX}-{SEQ}',
    created_at        timestamptz NOT NULL DEFAULT now(),
    updated_at        timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT fk_cotton_lot_sequences_tenant FOREIGN KEY (tenant_id)
        REFERENCES tenants (id) ON DELETE RESTRICT,
    CONSTRAINT uq_cotton_lot_sequences_tenant UNIQUE (tenant_id),
    CONSTRAINT ck_cotton_lot_sequences_seq_nonneg CHECK (current_sequence >= 0),
    CONSTRAINT ck_cotton_lot_sequences_padding CHECK (padding_width BETWEEN 1 AND 20)
);
CREATE INDEX idx_cotton_lot_sequences_tenant ON cotton_lot_sequences (tenant_id);
CREATE TRIGGER trg_cotton_lot_sequences_updated_at BEFORE UPDATE ON cotton_lot_sequences
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE OR REPLACE FUNCTION next_cotton_lot_serial(p_tenant_id uuid)
RETURNS text
LANGUAGE plpgsql
AS $$
DECLARE
    v_seq      bigint;
    v_prefix   text;
    v_padding  int;
    v_template text;
BEGIN
    UPDATE cotton_lot_sequences
       SET current_sequence = current_sequence + 1
     WHERE tenant_id = p_tenant_id
    RETURNING current_sequence, prefix, padding_width, format_template
         INTO v_seq, v_prefix, v_padding, v_template;

    IF NOT FOUND THEN
        RAISE EXCEPTION 'No cotton_lot_sequence configured for tenant %', p_tenant_id;
    END IF;

    RETURN replace(
             replace(v_template, '{PREFIX}', COALESCE(v_prefix, '')),
             '{SEQ}', lpad(v_seq::text, v_padding, '0')
           );
END;
$$;

-- Seed for RK Enterprises
INSERT INTO cotton_lot_sequences (tenant_id, current_sequence, prefix, padding_width, format_template)
VALUES ('11111111-1111-1111-1111-111111111111', 0, 'CTNL', 4, '{PREFIX}-{SEQ}');
