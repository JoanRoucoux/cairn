package com.roucoux.cairn.adapter.persistence.entity;

import com.roucoux.cairn.domain.model.Language;
import com.roucoux.cairn.domain.model.Preferences;
import com.roucoux.cairn.domain.model.Theme;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "preferences")
public class PreferencesEntity {

    public static final short SINGLE_ROW_ID = 1;

    @Id
    private short id;

    @Convert(converter = LanguageConverter.class)
    @Column(nullable = false, length = 2)
    private Language language;

    @Convert(converter = ThemeConverter.class)
    @Column(nullable = false, length = 6)
    private Theme theme;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected PreferencesEntity() {}

    public static PreferencesEntity fromDomain(Preferences preferences) {
        PreferencesEntity entity = new PreferencesEntity();
        entity.id = SINGLE_ROW_ID;
        entity.language = preferences.language();
        entity.theme = preferences.theme();
        entity.updatedAt = Instant.now();
        return entity;
    }

    public Preferences toDomain() {
        return new Preferences(language, theme);
    }
}
