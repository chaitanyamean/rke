package com.rke.backend.domain.converter;

import com.rke.backend.domain.enums.TransactionType;

import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class TransactionTypeConverter extends EnumCodeConverter<TransactionType> {
    public TransactionTypeConverter() {
        super(TransactionType.class);
    }
}
