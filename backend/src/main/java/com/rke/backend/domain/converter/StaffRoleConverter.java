package com.rke.backend.domain.converter;

import com.rke.backend.domain.enums.StaffRole;

import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class StaffRoleConverter extends EnumCodeConverter<StaffRole> {
    public StaffRoleConverter() {
        super(StaffRole.class);
    }
}
