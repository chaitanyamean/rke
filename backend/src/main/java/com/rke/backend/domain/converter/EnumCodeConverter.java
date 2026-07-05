package com.rke.backend.domain.converter;

import jakarta.persistence.AttributeConverter;

/**
 * Maps a Java enum to/from its lowercase DB token (e.g. {@code CASH_SALE} &lt;-&gt;
 * {@code cash_sale}). This keeps the Java enum constants idiomatic while matching
 * the lowercase values enforced by the CHECK constraints in the schema.
 */
public abstract class EnumCodeConverter<E extends Enum<E>> implements AttributeConverter<E, String> {

    private final Class<E> type;

    protected EnumCodeConverter(Class<E> type) {
        this.type = type;
    }

    @Override
    public String convertToDatabaseColumn(E attribute) {
        return attribute == null ? null : attribute.name().toLowerCase();
    }

    @Override
    public E convertToEntityAttribute(String dbData) {
        return dbData == null ? null : Enum.valueOf(type, dbData.toUpperCase());
    }
}
