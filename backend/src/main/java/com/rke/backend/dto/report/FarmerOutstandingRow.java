package com.rke.backend.dto.report;

import java.math.BigDecimal;

/**
 * One row in the Farmer / Village Outstandings report.
 *
 * <p>Sign convention (aligns with TransactionClassifier):
 * <ul>
 *   <li>{@code totalDebits}   – sum of all DEBIT-classified transactions (always ≥ 0)</li>
 *   <li>{@code totalCredits}  – sum of all CREDIT-classified transactions (always ≥ 0)</li>
 *   <li>{@code totalInterest} – reserved for future interest computation (always 0 for now)</li>
 *   <li>{@code outstandingBalance} = totalCredits – totalDebits – totalInterest
 *       <br>positive → firm owes farmer; negative → farmer owes firm</li>
 * </ul>
 */
public record FarmerOutstandingRow(
        String farmerId,
        String farmerName,
        String fatherName,
        String villageName,
        BigDecimal totalDebits,
        BigDecimal totalCredits,
        BigDecimal totalInterest,
        BigDecimal outstandingBalance) {
}
