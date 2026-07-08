package com.rke.backend.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.rke.backend.domain.Transaction;
import com.rke.backend.domain.enums.TransactionStatus;
import com.rke.backend.domain.enums.TransactionType;

public record TransactionResponse(
        UUID id,
        /** Human-readable transaction number: {YYYY}-{billNumber}-{increment}. */
        String transactionNo,
        UUID farmerId,
        String billNumber,
        /** Non-null only for RETURN transactions. */
        String originalBillNumber,
        UUID billNumberTypeId,
        TransactionType transactionType,
        LocalDate transactionDate,
        BigDecimal grandTotal,
        String remarks,
        TransactionStatus status,
        List<TransactionItemResponse> items
) {
    public static TransactionResponse from(Transaction tx, List<TransactionItemResponse> items) {
        return new TransactionResponse(
                tx.getId(),
                tx.getTransactionNo(),
                tx.getFarmerId(),
                tx.getBillNumber(),
                tx.getOriginalBillNumber(),
                tx.getBillNumberTypeId(),
                tx.getTransactionType(),
                tx.getTransactionDate(),
                tx.getGrandTotal(),
                tx.getRemarks(),
                tx.getStatus(),
                items
        );
    }
}
