-- =============================================================================
-- V1 — Core schema for RK Enterprises (multi-tenant agricultural dealership).
--
-- Conventions enforced across this migration:
--   * Every table has: id (uuid, default gen_random_uuid()), created_at,
--     updated_at (timestamptz, default now()). updated_at is kept fresh by the
--     shared set_updated_at() trigger.
--   * Financial / transactional tables additionally carry:
--       deleted_at  (soft delete — these rows are NEVER hard-deleted)
--       voided_at + void_reason (reversals)
--   * Quantity / amount / price columns carry CHECK constraints (> 0, or >= 0
--     where zero is legitimately valid).
--   * Foreign keys use ON DELETE RESTRICT — financial history is never
--     cascade-deleted.
--   * Every table except `tenants` (and super_admin rows in staff_users) carries
--     tenant_id.
-- =============================================================================

CREATE EXTENSION IF NOT EXISTS pgcrypto;   -- gen_random_uuid()

-- -----------------------------------------------------------------------------
-- Shared trigger: keep updated_at current on every UPDATE.
-- -----------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION set_updated_at()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    NEW.updated_at = now();
    RETURN NEW;
END;
$$;

-- =============================================================================
-- 1. tenants — one row per dealership customer. RK Enterprises is seeded in V2.
--    (No tenant_id: this IS the tenant table.)
-- =============================================================================
CREATE TABLE tenants (
    id            uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    name          text NOT NULL,
    slug          text NOT NULL,
    logo_url      text,
    primary_color text,
    active        boolean NOT NULL DEFAULT true,
    created_at    timestamptz NOT NULL DEFAULT now(),
    updated_at    timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uq_tenants_slug UNIQUE (slug)
);
CREATE TRIGGER trg_tenants_updated_at BEFORE UPDATE ON tenants
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- =============================================================================
-- 2. villages — master data. All village references elsewhere are FKs here;
--    no free-text village entry is allowed anywhere else.
-- =============================================================================
CREATE TABLE villages (
    id         uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id  uuid NOT NULL,
    name       text NOT NULL,
    landmark   text,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT fk_villages_tenant FOREIGN KEY (tenant_id)
        REFERENCES tenants (id) ON DELETE RESTRICT,
    CONSTRAINT uq_villages_tenant_name UNIQUE (tenant_id, name)
);
CREATE INDEX idx_villages_tenant ON villages (tenant_id);
CREATE TRIGGER trg_villages_updated_at BEFORE UPDATE ON villages
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- =============================================================================
-- 3. item_categories — master data (e.g. Cotton, Seed, Fertilizer, Pesticide).
-- =============================================================================
CREATE TABLE item_categories (
    id          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id   uuid NOT NULL,
    name        text NOT NULL,
    description text,
    created_at  timestamptz NOT NULL DEFAULT now(),
    updated_at  timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT fk_item_categories_tenant FOREIGN KEY (tenant_id)
        REFERENCES tenants (id) ON DELETE RESTRICT,
    CONSTRAINT uq_item_categories_tenant_name UNIQUE (tenant_id, name)
);
CREATE INDEX idx_item_categories_tenant ON item_categories (tenant_id);
CREATE TRIGGER trg_item_categories_updated_at BEFORE UPDATE ON item_categories
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- =============================================================================
-- 4. farmers
--    Natural identity (for now): name + father_name + village.
--    NOTE (needs client confirmation): we treat (name, father_name, village_id)
--    as unique per tenant as a PLACEHOLDER. Real-world data may legitimately
--    contain true duplicates (two different people, same name/father/village).
--    Confirm with the client before relying on this; the constraint may need to
--    be relaxed or replaced with a soft "possible duplicate" warning instead.
--    Also note: father_name NULLs are treated as distinct by Postgres, so the
--    constraint does not prevent duplicates when father_name is unknown.
-- =============================================================================
CREATE TABLE farmers (
    id            uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id     uuid NOT NULL,
    name          text NOT NULL,
    father_name   text,
    village_id    uuid NOT NULL,
    address       text,
    mobile_number text,
    reference     text,
    created_at    timestamptz NOT NULL DEFAULT now(),
    updated_at    timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT fk_farmers_tenant FOREIGN KEY (tenant_id)
        REFERENCES tenants (id) ON DELETE RESTRICT,
    CONSTRAINT fk_farmers_village FOREIGN KEY (village_id)
        REFERENCES villages (id) ON DELETE RESTRICT,
    CONSTRAINT uq_farmers_identity UNIQUE (tenant_id, name, father_name, village_id)
);
CREATE INDEX idx_farmers_tenant ON farmers (tenant_id);
CREATE INDEX idx_farmers_village ON farmers (village_id);
CREATE TRIGGER trg_farmers_updated_at BEFORE UPDATE ON farmers
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- =============================================================================
-- 5. items — catalog, priced for both credit and cash sales.
-- =============================================================================
CREATE TABLE items (
    id               uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id        uuid NOT NULL,
    item_category_id uuid NOT NULL,
    name             text NOT NULL,
    credit_price     numeric(12,2) NOT NULL DEFAULT 0,
    cash_price       numeric(12,2) NOT NULL DEFAULT 0,
    created_at       timestamptz NOT NULL DEFAULT now(),
    updated_at       timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT fk_items_tenant FOREIGN KEY (tenant_id)
        REFERENCES tenants (id) ON DELETE RESTRICT,
    CONSTRAINT fk_items_category FOREIGN KEY (item_category_id)
        REFERENCES item_categories (id) ON DELETE RESTRICT,
    CONSTRAINT uq_items_tenant_category_name UNIQUE (tenant_id, item_category_id, name),
    CONSTRAINT ck_items_credit_price_nonneg CHECK (credit_price >= 0),
    CONSTRAINT ck_items_cash_price_nonneg   CHECK (cash_price >= 0)
);
CREATE INDEX idx_items_tenant ON items (tenant_id);
CREATE INDEX idx_items_category ON items (item_category_id);
CREATE TRIGGER trg_items_updated_at BEFORE UPDATE ON items
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- =============================================================================
-- 6. bill_number_types — master data, scoped per item category. Replaces any
--    hardcoded bill-type codes.
-- =============================================================================
CREATE TABLE bill_number_types (
    id               uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id        uuid NOT NULL,
    name             text NOT NULL,
    item_category_id uuid NOT NULL,
    description      text,
    created_at       timestamptz NOT NULL DEFAULT now(),
    updated_at       timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT fk_bill_number_types_tenant FOREIGN KEY (tenant_id)
        REFERENCES tenants (id) ON DELETE RESTRICT,
    CONSTRAINT fk_bill_number_types_category FOREIGN KEY (item_category_id)
        REFERENCES item_categories (id) ON DELETE RESTRICT,
    CONSTRAINT uq_bill_number_types UNIQUE (tenant_id, item_category_id, name)
);
CREATE INDEX idx_bill_number_types_tenant ON bill_number_types (tenant_id);
CREATE TRIGGER trg_bill_number_types_updated_at BEFORE UPDATE ON bill_number_types
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- =============================================================================
-- 7. bill_number_sequences — backs auto-generation of bill numbers.
--    The exact bill number FORMAT is NOT yet confirmed by the client, so the
--    format is fully configurable per (tenant, category) via a template with
--    placeholders instead of a hardcoded prefix scheme:
--       format_template : any string containing {PREFIX} and {SEQ}
--       prefix          : substituted for {PREFIX}
--       padding_width    : zero-pad width for the sequence, substituted for {SEQ}
--    e.g. template '{PREFIX}{SEQ}', prefix 'RKE/', padding 6, seq 42 -> 'RKE/000042'.
--    Change these values (not code) once the real format is confirmed.
-- =============================================================================
CREATE TABLE bill_number_sequences (
    id               uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id        uuid NOT NULL,
    item_category_id uuid NOT NULL,
    current_sequence bigint NOT NULL DEFAULT 0,
    prefix           text NOT NULL DEFAULT '',
    padding_width    int  NOT NULL DEFAULT 6,
    format_template  text NOT NULL DEFAULT '{PREFIX}{SEQ}',
    created_at       timestamptz NOT NULL DEFAULT now(),
    updated_at       timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT fk_bill_number_sequences_tenant FOREIGN KEY (tenant_id)
        REFERENCES tenants (id) ON DELETE RESTRICT,
    CONSTRAINT fk_bill_number_sequences_category FOREIGN KEY (item_category_id)
        REFERENCES item_categories (id) ON DELETE RESTRICT,
    CONSTRAINT uq_bill_number_sequences UNIQUE (tenant_id, item_category_id),
    CONSTRAINT ck_bill_number_sequences_seq_nonneg CHECK (current_sequence >= 0),
    CONSTRAINT ck_bill_number_sequences_padding CHECK (padding_width BETWEEN 1 AND 20)
);
CREATE INDEX idx_bill_number_sequences_tenant ON bill_number_sequences (tenant_id);
CREATE TRIGGER trg_bill_number_sequences_updated_at BEFORE UPDATE ON bill_number_sequences
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- =============================================================================
-- 8. transactions — FINANCIAL (soft-delete + void).
--    bill_number is UNIQUE scoped PER TENANT (not globally).
--    NOTE: the general project rule mentioned "globally unique for now", but the
--    explicit spec for this table is per-tenant uniqueness ("not globally"), so
--    that is what is implemented. Revisit if a global registry is ever needed.
-- =============================================================================
CREATE TABLE transactions (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id           uuid NOT NULL,
    farmer_id           uuid NOT NULL,
    bill_number         text NOT NULL,
    bill_number_type_id uuid,
    transaction_type    text NOT NULL,   -- enum: cash_sale/credit_sale/cash_payment/cash_receipt/return
    transaction_date    date NOT NULL DEFAULT CURRENT_DATE,
    grand_total         numeric(14,2) NOT NULL DEFAULT 0,
    remarks             text,
    status              text NOT NULL DEFAULT 'active',   -- enum: active/voided
    -- financial reversal / soft-delete columns
    deleted_at          timestamptz,
    voided_at           timestamptz,
    void_reason         text,
    created_at          timestamptz NOT NULL DEFAULT now(),
    updated_at          timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT fk_transactions_tenant FOREIGN KEY (tenant_id)
        REFERENCES tenants (id) ON DELETE RESTRICT,
    CONSTRAINT fk_transactions_farmer FOREIGN KEY (farmer_id)
        REFERENCES farmers (id) ON DELETE RESTRICT,
    CONSTRAINT fk_transactions_bill_number_type FOREIGN KEY (bill_number_type_id)
        REFERENCES bill_number_types (id) ON DELETE RESTRICT,
    CONSTRAINT uq_transactions_tenant_bill_number UNIQUE (tenant_id, bill_number),
    CONSTRAINT ck_transactions_grand_total_nonneg CHECK (grand_total >= 0),
    CONSTRAINT ck_transactions_type CHECK (transaction_type IN
        ('cash_sale','credit_sale','cash_payment','cash_receipt','return')),
    CONSTRAINT ck_transactions_status CHECK (status IN ('active','voided'))
);
CREATE INDEX idx_transactions_tenant ON transactions (tenant_id);
CREATE INDEX idx_transactions_farmer ON transactions (farmer_id);
CREATE TRIGGER trg_transactions_updated_at BEFORE UPDATE ON transactions
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- =============================================================================
-- 9. transaction_items — FINANCIAL line items (soft-delete + void). Carries
--    tenant_id per the "every table carries tenant_id" rule.
-- =============================================================================
CREATE TABLE transaction_items (
    id             uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id      uuid NOT NULL,
    transaction_id uuid NOT NULL,
    item_id        uuid NOT NULL,
    quantity       numeric(12,3) NOT NULL,
    price          numeric(12,2) NOT NULL,
    amount         numeric(14,2) NOT NULL,
    deleted_at     timestamptz,
    voided_at      timestamptz,
    void_reason    text,
    created_at     timestamptz NOT NULL DEFAULT now(),
    updated_at     timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT fk_transaction_items_tenant FOREIGN KEY (tenant_id)
        REFERENCES tenants (id) ON DELETE RESTRICT,
    CONSTRAINT fk_transaction_items_transaction FOREIGN KEY (transaction_id)
        REFERENCES transactions (id) ON DELETE RESTRICT,
    CONSTRAINT fk_transaction_items_item FOREIGN KEY (item_id)
        REFERENCES items (id) ON DELETE RESTRICT,
    CONSTRAINT ck_transaction_items_quantity_pos CHECK (quantity > 0),
    CONSTRAINT ck_transaction_items_price_nonneg  CHECK (price >= 0),
    CONSTRAINT ck_transaction_items_amount_nonneg CHECK (amount >= 0)
);
CREATE INDEX idx_transaction_items_tenant ON transaction_items (tenant_id);
CREATE INDEX idx_transaction_items_transaction ON transaction_items (transaction_id);
CREATE INDEX idx_transaction_items_item ON transaction_items (item_id);
CREATE TRIGGER trg_transaction_items_updated_at BEFORE UPDATE ON transaction_items
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- =============================================================================
-- 10. cotton_lots — FINANCIAL (soft-delete + void).
--     vehicle_serial_number is auto-generated and UNIQUE per tenant. Generation
--     is handled by the service layer (a future phase); the DB guarantees
--     uniqueness within a tenant.
-- =============================================================================
CREATE TABLE cotton_lots (
    id                          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id                   uuid NOT NULL,
    vehicle_serial_number       text NOT NULL,
    vehicle_registration_number text,
    muta_hamali_name            text,
    common_price                numeric(12,2) NOT NULL DEFAULT 0,
    total_quantity              numeric(14,3) NOT NULL DEFAULT 0,
    total_amount                numeric(16,2) NOT NULL DEFAULT 0,
    lot_date                    date NOT NULL DEFAULT CURRENT_DATE,
    deleted_at                  timestamptz,
    voided_at                   timestamptz,
    void_reason                 text,
    created_at                  timestamptz NOT NULL DEFAULT now(),
    updated_at                  timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT fk_cotton_lots_tenant FOREIGN KEY (tenant_id)
        REFERENCES tenants (id) ON DELETE RESTRICT,
    CONSTRAINT uq_cotton_lots_tenant_serial UNIQUE (tenant_id, vehicle_serial_number),
    CONSTRAINT ck_cotton_lots_common_price_nonneg CHECK (common_price >= 0),
    CONSTRAINT ck_cotton_lots_total_quantity_nonneg CHECK (total_quantity >= 0),
    CONSTRAINT ck_cotton_lots_total_amount_nonneg CHECK (total_amount >= 0)
);
CREATE INDEX idx_cotton_lots_tenant ON cotton_lots (tenant_id);
CREATE TRIGGER trg_cotton_lots_updated_at BEFORE UPDATE ON cotton_lots
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- =============================================================================
-- 11. cotton_lot_entries — FINANCIAL per-farmer lines within a cotton lot.
-- =============================================================================
CREATE TABLE cotton_lot_entries (
    id            uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id     uuid NOT NULL,
    cotton_lot_id uuid NOT NULL,
    farmer_id     uuid NOT NULL,
    village_id    uuid NOT NULL,
    quantity      numeric(12,3) NOT NULL,
    price         numeric(12,2) NOT NULL,
    amount        numeric(14,2) NOT NULL,
    deleted_at    timestamptz,
    voided_at     timestamptz,
    void_reason   text,
    created_at    timestamptz NOT NULL DEFAULT now(),
    updated_at    timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT fk_cotton_lot_entries_tenant FOREIGN KEY (tenant_id)
        REFERENCES tenants (id) ON DELETE RESTRICT,
    CONSTRAINT fk_cotton_lot_entries_lot FOREIGN KEY (cotton_lot_id)
        REFERENCES cotton_lots (id) ON DELETE RESTRICT,
    CONSTRAINT fk_cotton_lot_entries_farmer FOREIGN KEY (farmer_id)
        REFERENCES farmers (id) ON DELETE RESTRICT,
    CONSTRAINT fk_cotton_lot_entries_village FOREIGN KEY (village_id)
        REFERENCES villages (id) ON DELETE RESTRICT,
    CONSTRAINT ck_cotton_lot_entries_quantity_pos CHECK (quantity > 0),
    CONSTRAINT ck_cotton_lot_entries_price_nonneg  CHECK (price >= 0),
    CONSTRAINT ck_cotton_lot_entries_amount_nonneg CHECK (amount >= 0)
);
CREATE INDEX idx_cotton_lot_entries_tenant ON cotton_lot_entries (tenant_id);
CREATE INDEX idx_cotton_lot_entries_lot ON cotton_lot_entries (cotton_lot_id);
CREATE INDEX idx_cotton_lot_entries_farmer ON cotton_lot_entries (farmer_id);
CREATE TRIGGER trg_cotton_lot_entries_updated_at BEFORE UPDATE ON cotton_lot_entries
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- =============================================================================
-- 13. staff_users — application logins.
--     username is UNIQUE scoped PER TENANT (two tenants can each have a "raj").
--     super_admin is NOT scoped to any tenant (tenant_id IS NULL) and can access
--     the cross-tenant Super Admin panel (Phase 2.5).
--     (Created before audit_log because audit_log.changed_by references it.)
-- =============================================================================
CREATE TABLE staff_users (
    id            uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id     uuid,                       -- NULL only for super_admin
    username      text NOT NULL,
    password_hash text NOT NULL,
    full_name     text,
    role          text NOT NULL,              -- enum: super_admin/admin/staff
    active        boolean NOT NULL DEFAULT true,
    created_at    timestamptz NOT NULL DEFAULT now(),
    updated_at    timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT fk_staff_users_tenant FOREIGN KEY (tenant_id)
        REFERENCES tenants (id) ON DELETE RESTRICT,
    CONSTRAINT ck_staff_users_role CHECK (role IN ('super_admin','admin','staff')),
    -- super_admin => no tenant; every other role => must belong to a tenant.
    CONSTRAINT ck_staff_users_tenant_scope CHECK (
        (role = 'super_admin' AND tenant_id IS NULL)
        OR (role <> 'super_admin' AND tenant_id IS NOT NULL)
    )
);
-- Tenant-scoped uniqueness for tenant users...
CREATE UNIQUE INDEX uq_staff_users_tenant_username
    ON staff_users (tenant_id, username) WHERE tenant_id IS NOT NULL;
-- ...and global uniqueness among super_admins (where tenant_id IS NULL, so the
-- composite index above does not enforce it).
CREATE UNIQUE INDEX uq_staff_users_superadmin_username
    ON staff_users (username) WHERE tenant_id IS NULL;
CREATE INDEX idx_staff_users_tenant ON staff_users (tenant_id);
CREATE TRIGGER trg_staff_users_updated_at BEFORE UPDATE ON staff_users
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- =============================================================================
-- 12. audit_log — append-only-ish change log. Not soft-deleted.
-- =============================================================================
CREATE TABLE audit_log (
    id         uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id  uuid NOT NULL,
    table_name text NOT NULL,
    record_id  uuid NOT NULL,
    action     text NOT NULL,               -- enum: insert/update/void
    changed_by uuid,
    old_values jsonb,
    new_values jsonb,
    changed_at timestamptz NOT NULL DEFAULT now(),
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT fk_audit_log_tenant FOREIGN KEY (tenant_id)
        REFERENCES tenants (id) ON DELETE RESTRICT,
    CONSTRAINT fk_audit_log_changed_by FOREIGN KEY (changed_by)
        REFERENCES staff_users (id) ON DELETE RESTRICT,
    CONSTRAINT ck_audit_log_action CHECK (action IN ('insert','update','void'))
);
CREATE INDEX idx_audit_log_tenant ON audit_log (tenant_id);
CREATE INDEX idx_audit_log_record ON audit_log (table_name, record_id);
CREATE TRIGGER trg_audit_log_updated_at BEFORE UPDATE ON audit_log
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- =============================================================================
-- 14. tenant_features — per-tenant feature entitlements. A feature_key absent
--     for a tenant is treated as DISABLED by default, so a brand-new tenant
--     starts with nothing extra until a super_admin turns features on.
-- =============================================================================
CREATE TABLE tenant_features (
    id          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id   uuid NOT NULL,
    feature_key text NOT NULL,
    enabled     boolean NOT NULL DEFAULT true,
    created_at  timestamptz NOT NULL DEFAULT now(),
    updated_at  timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT fk_tenant_features_tenant FOREIGN KEY (tenant_id)
        REFERENCES tenants (id) ON DELETE RESTRICT,
    CONSTRAINT uq_tenant_features UNIQUE (tenant_id, feature_key)
);
CREATE INDEX idx_tenant_features_tenant ON tenant_features (tenant_id);
CREATE TRIGGER trg_tenant_features_updated_at BEFORE UPDATE ON tenant_features
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- =============================================================================
-- next_bill_number(tenant_id, category_id)
--   Atomically increments the (tenant, category) sequence and returns a bill
--   number formatted from the configurable template. Concurrency-safe: the
--   single UPDATE ... RETURNING takes a row lock and increments atomically, so
--   two concurrent callers can never receive the same sequence value.
-- =============================================================================
CREATE OR REPLACE FUNCTION next_bill_number(p_tenant_id uuid, p_category_id uuid)
RETURNS text
LANGUAGE plpgsql
AS $$
DECLARE
    v_seq      bigint;
    v_prefix   text;
    v_padding  int;
    v_template text;
BEGIN
    UPDATE bill_number_sequences
       SET current_sequence = current_sequence + 1
     WHERE tenant_id = p_tenant_id
       AND item_category_id = p_category_id
    RETURNING current_sequence, prefix, padding_width, format_template
         INTO v_seq, v_prefix, v_padding, v_template;

    IF NOT FOUND THEN
        RAISE EXCEPTION 'No bill_number_sequence configured for tenant % and category %',
            p_tenant_id, p_category_id;
    END IF;

    RETURN replace(
             replace(v_template, '{PREFIX}', COALESCE(v_prefix, '')),
             '{SEQ}', lpad(v_seq::text, v_padding, '0')
           );
END;
$$;
