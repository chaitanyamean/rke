# Farmer Ledger Sign Convention

## Introduction

This document defines the sign convention for the Farmer Ledger, classifying each transaction type as either a Debit or Credit. It also documents the outstanding balance formula used by `TransactionClassifier.java` and `ReportService.java`. The goal is to provide a single authoritative reference so that any future reclassification or balance logic change can be made with full understanding of the design intent.

---

## Classification Table

| Transaction Type | Direction | Description of financial impact |
|------------------|-----------|----------------------------------|
| `CASH_SALE`      | Debit     | The farmer purchases items on cash; increases what they owe |
| `CREDIT_SALE`    | Debit     | The farmer purchases items on credit; increases what they owe |
| `CASH_PAYMENT`   | Debit *(provisional — see section below)* | Classified as a debit under the assumption that it is a distinct settlement (e.g. cotton procurement), not a reduction against Credit Sale debt |
| `CASH_RECEIPT`   | Credit    | Money received from the farmer; decreases what they owe |
| `RETURN`         | Credit    | Items returned by the farmer; decreases what they owe |

---

## Outstanding Balance Formula

```
outstanding = ABS(SUM(credits)) − ABS(SUM(debits))
```

**Sign convention:**

- **Negative outstanding** — the farmer owes the firm (debits exceed credits)
- **Positive outstanding** — the firm owes the farmer (credits exceed debits)
- **Zero** — the account is settled

Voided transactions (rows with `status = 'voided'`) are excluded from all balance and ledger calculations.

---

## Provisional: `cash_payment` Classification

### Assumption

`CASH_PAYMENT` is currently classified as a **Debit** (i.e., it increases what the farmer owes the firm). This classification rests on the assumption that a payment is a **distinct settlement transaction** — for example, the firm paying the farmer for cotton procurement — and is **not** a reduction applied against an outstanding Credit Sale debt.

Under this assumption, both the payment from the firm to the farmer and the goods sold on credit to the farmer run as separate debit entries, and only `CASH_RECEIPT` (money flowing back from the farmer) acts as a credit that reduces the balance.

If this assumption is incorrect — that is, if `CASH_PAYMENT` should instead represent money that the farmer pays to reduce what they owe — then `CASH_PAYMENT` should be reclassified as a **Credit**.

### Where the classification is defined

The single code location that controls this classification is:

**Class:** `TransactionClassifier.java`  
**Package:** `com.rke.backend.domain.ledger`

All balance and ledger calculations in the codebase call through this single component. No other class duplicates this classification logic.

### How to change it

To reclassify `cash_payment` as a **Credit** (i.e., it reduces what the farmer owes), change the single line in `TransactionClassifier.java` from:

```java
case CASH_PAYMENT -> LedgerDirection.DEBIT;
```

to:

```java
case CASH_PAYMENT -> LedgerDirection.CREDIT;
```

Because `TransactionClassifier` is the sole source of truth, this one-line change propagates automatically to `ReportService.java`, `PaymentService.java`, and every other consumer. No other code changes are required.
