package com.rke.backend.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.rke.backend.domain.Transaction;
import com.rke.backend.domain.enums.TransactionType;

/**
 * Original CASH_SALE or CREDIT_SALE transaction returned by the "lookup by bill
 * number" endpoint used to start a return. Line items carry remaining-returnable
 * quantity (see {@link OriginalSaleItemResponse}) so the frontend never needs to
 * re-derive cumulative-return math itself.
 */
public record OriginalSaleResponse(
        UUID id,
        String transactionNo,
        UUID farmerId,
        String billNumber,
        TransactionType transactionType,
        LocalDate transactionDate,
        BigDecimal grandTotal,
        List<OriginalSaleItemResponse> items
) {
    public static OriginalSaleResponse of(Transaction tx, List<OriginalSaleItemResponse> items) {
        return new OriginalSaleResponse(
                tx.getId(),
                tx.getTransactionNo(),
                tx.getFarmerId(),
                tx.getBillNumber(),
                tx.getTransactionType(),
                tx.getTransactionDate(),
                tx.getGrandTotal(),
                items
        );
    }
}
