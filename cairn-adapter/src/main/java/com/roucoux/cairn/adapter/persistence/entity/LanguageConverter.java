package com.roucoux.cairn.adapter.persistence.entity;

import com.roucoux.cairn.domain.model.Language;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.util.Locale;

@Converter(autoApply = false)
public class LanguageConverter implements AttributeConverter<Language, String> {

    @Override
    public String convertToDatabaseColumn(Language language) {
        return language == null ? null : language.name().toLowerCase(Locale.ROOT);
    }

    @Override
    public Language convertToEntityAttribute(String value) {
        return value == null ? null : Language.valueOf(value.toUpperCase(Locale.ROOT));
    }
}
