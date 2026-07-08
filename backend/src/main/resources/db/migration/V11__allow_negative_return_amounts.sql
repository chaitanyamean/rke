-- =============================================================================
-- V11 — Allow negative grand_total / amount for RETURN transactions.
--
-- ReturnService (see Javadoc on Transaction.originalBillNumber and
-- ReturnService.createReturn) deliberately stores return line items and the
-- return's grand_total as NEGATIVE values, so that summing transaction_items.amount
-- (or transactions.grand_total) directly nets sales and returns without needing a
-- separate is_return flag. ReportService already relies on this sign convention.
--
-- The original V1 CHECK constraints (grand_total >= 0, amount >= 0) predate the
-- return feature and blocked exactly the negative values returns are designed to
-- write. This migration relaxes both constraints to allow negative values ONLY
-- for the express purpose of that documented sign convention:
--   * transactions.grand_total may be negative only for transaction_type = 'return'
--     (all other types keep the original >= 0 rule).
--   * transaction_items.amount may be negative (its sign now simply follows the
--     parent transaction's type; a positive-only rule can't be expressed cleanly
--     at the row level without a join, so this one is relaxed to "any value" —
--     quantity > 0 and price >= 0 remain enforced separately, which already rules
--     out garbage rows).
-- =============================================================================

ALTER TABLE transactions
    DROP CONSTRAINT ck_transactions_grand_total_nonneg;

ALTER TABLE transactions
    ADD CONSTRAINT ck_transactions_grand_total_sign CHECK (
        (transaction_type = 'return' AND grand_total <= 0)
        OR (transaction_type <> 'return' AND grand_total >= 0)
    );

ALTER TABLE transaction_items
    DROP CONSTRAINT ck_transaction_items_amount_nonneg;
-- No replacement sign-scoped CHECK here: transaction_items has no transaction_type
-- of its own (it would need a join to transactions), and price/quantity are still
-- individually constrained (quantity > 0, price >= 0) so this only relaxes the
-- sign of the computed amount = quantity * price for return rows.
