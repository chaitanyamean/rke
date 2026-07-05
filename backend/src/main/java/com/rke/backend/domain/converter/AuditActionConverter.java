package com.rke.backend.domain.converter;

import com.rke.backend.domain.enums.AuditAction;

import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class AuditActionConverter extends EnumCodeConverter<AuditAction> {
    public AuditActionConverter() {
        super(AuditAction.class);
    }
}
