package com.roucoux.cairn.adapter.persistence.entity;

import com.roucoux.cairn.domain.model.Theme;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.util.Locale;

@Converter(autoApply = false)
public class ThemeConverter implements AttributeConverter<Theme, String> {

    @Override
    public String convertToDatabaseColumn(Theme theme) {
        return theme == null ? null : theme.name().toLowerCase(Locale.ROOT);
    }

    @Override
    public Theme convertToEntityAttribute(String value) {
        return value == null ? null : Theme.valueOf(value.toUpperCase(Locale.ROOT));
    }
}
