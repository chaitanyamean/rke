package com.rke.backend.domain.ledger;

import com.rke.backend.domain.enums.TransactionType;
import java.math.BigDecimal;

/**
 * Single source of truth for how each transaction type is classified in the
 * farmer ledger. Every place that computes an outstanding balance or ledger
 * direction MUST call through this class — do not duplicate this mapping.
 *
 * <p>Classification table:
 * <pre>
 *   DEBIT  (increases what farmer owes): CASH_SALE, CREDIT_SALE, CASH_PAYMENT*
 *   CREDIT (decreases what farmer owes): CASH_RECEIPT, RETURN
 * </pre>
 *
 * * PROVISIONAL: see {@link #classify(TransactionType)} for the cash_payment note.
 *
 * @see /docs/farmer-ledger-sign-convention.md
 */
public final class TransactionClassifier {

    private TransactionClassifier() {}

    /**
     * Returns the ledger direction for the given transaction type.
     *
     * @throws IllegalArgumentException for null or any future unhandled type.
     */
    public static LedgerDirection classify(TransactionType type) {
        if (type == null) {
            throw new IllegalArgumentException("TransactionType must not be null");
        }
        return switch (type) {
            case CASH_SALE    -> LedgerDirection.DEBIT;
            case CREDIT_SALE  -> LedgerDirection.DEBIT;

            // PROVISIONAL: cash_payment is classified as a debit (increases farmer's owed balance)
            // under the assumption that Payment is a distinct settlement (e.g. cotton procurement)
            // separate from Credit Sale balances, NOT a reduction applied against Credit Sale debt.
            // If this assumption is wrong, only this one classification needs to change — see
            // /docs/farmer-ledger-sign-convention.md for the full context.
            case CASH_PAYMENT -> LedgerDirection.DEBIT;

            case CASH_RECEIPT -> LedgerDirection.CREDIT;
            case RETURN       -> LedgerDirection.CREDIT;
        };
    }

    /** Convenience: true when the type increases what the farmer owes. */
    public static boolean isDebit(TransactionType type) {
        return classify(type) == LedgerDirection.DEBIT;
    }

    /** Convenience: true when the type decreases what the farmer owes. */
    public static boolean isCredit(TransactionType type) {
        return classify(type) == LedgerDirection.CREDIT;
    }

    /**
     * Returns the signed ledger contribution of {@code amount}:
     *   – DEBIT  → {@code -amount}   (increases farmer's owed balance)
     *   – CREDIT → {@code +amount}   (decreases farmer's owed balance)
     *
     * <p>Both inputs and outputs are expressed as positive magnitudes with sign
     * applied here, so callers never need to remember the sign convention.
     */
    public static BigDecimal signedAmount(TransactionType type, BigDecimal amount) {
        var abs = amount.abs();
        return isDebit(type) ? abs.negate() : abs;
    }
}
