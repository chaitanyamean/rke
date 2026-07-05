package com.rke.backend.domain.converter;

import com.rke.backend.domain.enums.TransactionStatus;

import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class TransactionStatusConverter extends EnumCodeConverter<TransactionStatus> {
    public TransactionStatusConverter() {
        super(TransactionStatus.class);
    }
}
