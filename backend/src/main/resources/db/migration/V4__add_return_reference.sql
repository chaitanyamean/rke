-- Adds original_bill_number to the transactions table so that RETURN rows can
-- reference the sale they are reversing. NULL for every other transaction_type.
-- Indexed for the ReturnService cumulative-validation and reporting queries.

ALTER TABLE transactions
    ADD COLUMN original_bill_number VARCHAR(100) NULL;

CREATE INDEX idx_transactions_original_bill_number
    ON transactions(tenant_id, original_bill_number)
    WHERE original_bill_number IS NOT NULL;
