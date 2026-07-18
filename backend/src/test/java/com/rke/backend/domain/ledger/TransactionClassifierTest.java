package com.rke.backend.domain.ledger;

import static com.rke.backend.domain.enums.TransactionType.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

import com.rke.backend.domain.enums.TransactionType;

class TransactionClassifierTest {

    // -------------------------------------------------------------------------
    // 1. Every defined TransactionType has a known classification (non-null)
    // -------------------------------------------------------------------------

    @Test
    void allTypesHaveKnownClassification() {
        for (TransactionType type : TransactionType.values()) {
            assertThat(TransactionClassifier.classify(type))
                    .as("classify(%s) should return a non-null LedgerDirection", type)
                    .isNotNull();
        }
    }

    // -------------------------------------------------------------------------
    // 2. Debit types: CASH_SALE, CREDIT_SALE, CASH_PAYMENT
    // -------------------------------------------------------------------------

    @Test
    void debits_are_cashSale_creditSale_cashPayment() {
        assertThat(TransactionClassifier.isDebit(CASH_SALE)).isTrue();
        assertThat(TransactionClassifier.isDebit(CREDIT_SALE)).isTrue();
        assertThat(TransactionClassifier.isDebit(CASH_PAYMENT)).isTrue();
    }

    // -------------------------------------------------------------------------
    // 3. Credit types: CASH_RECEIPT, RETURN
    // -------------------------------------------------------------------------

    @Test
    void credits_are_cashReceipt_return() {
        assertThat(TransactionClassifier.isCredit(CASH_RECEIPT)).isTrue();
        assertThat(TransactionClassifier.isCredit(RETURN)).isTrue();
    }

    // -------------------------------------------------------------------------
    // 4. CASH_PAYMENT is provisionally a debit — this test locks in that choice.
    //    If the assumption changes, only TransactionClassifier needs updating.
    // -------------------------------------------------------------------------

    @Test
    void cashPayment_isProvisionallyDebit_lockIn() {
        // PROVISIONAL: cash_payment is classified as a debit under the assumption
        // that Payment is a distinct settlement separate from Credit Sale balances.
        // This test intentionally names the provisional status so any future
        // reclassification forces an explicit, visible decision here.
        assertThat(TransactionClassifier.isDebit(CASH_PAYMENT)).isTrue();
    }

    // -------------------------------------------------------------------------
    // 5. Scenario: credit_sale of ₹50 (debit) + cash_receipt of ₹30 (credit)
    //    → outstanding = −₹20 (farmer owes ₹20)
    // -------------------------------------------------------------------------

    @Test
    void creditSaleDebitAndCashReceiptCredit_outstandingIsNegative20() {
        BigDecimal creditSaleAmt  = BigDecimal.valueOf(50); // e.g. 10 units × ₹5
        BigDecimal cashReceiptAmt = BigDecimal.valueOf(30);

        BigDecimal outstanding =
                TransactionClassifier.signedAmount(CREDIT_SALE,  creditSaleAmt)
               .add(TransactionClassifier.signedAmount(CASH_RECEIPT, cashReceiptAmt));

        assertThat(outstanding).isEqualByComparingTo(new BigDecimal("-20"));
    }

    // -------------------------------------------------------------------------
    // 6. null input must throw IllegalArgumentException
    // -------------------------------------------------------------------------

    @Test
    void nullType_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> TransactionClassifier.classify(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // -------------------------------------------------------------------------
    // 7. signedAmount for a debit type returns a negative value
    // -------------------------------------------------------------------------

    @Test
    void signedAmount_debit_returnsNegative() {
        BigDecimal result = TransactionClassifier.signedAmount(CREDIT_SALE, BigDecimal.valueOf(50));
        assertThat(result).isEqualByComparingTo(new BigDecimal("-50"));
    }

    // -------------------------------------------------------------------------
    // 8. signedAmount for a credit type returns a positive value
    // -------------------------------------------------------------------------

    @Test
    void signedAmount_credit_returnsPositive() {
        BigDecimal result = TransactionClassifier.signedAmount(CASH_RECEIPT, BigDecimal.valueOf(30));
        assertThat(result).isEqualByComparingTo(new BigDecimal("30"));
    }
}
