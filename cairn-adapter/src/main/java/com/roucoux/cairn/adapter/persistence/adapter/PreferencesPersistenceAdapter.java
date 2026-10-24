package com.roucoux.cairn.adapter.persistence.adapter;

import com.roucoux.cairn.adapter.persistence.entity.PreferencesEntity;
import com.roucoux.cairn.adapter.persistence.repository.PreferencesJpaRepository;
import com.roucoux.cairn.domain.model.Preferences;
import com.roucoux.cairn.domain.port.out.LoadPreferencesPort;
import com.roucoux.cairn.domain.port.out.SavePreferencesPort;
import org.springframework.stereotype.Component;

@Component
public class PreferencesPersistenceAdapter implements LoadPreferencesPort, SavePreferencesPort {

    private final PreferencesJpaRepository repository;

    public PreferencesPersistenceAdapter(PreferencesJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public Preferences load() {
        return repository
                .findById(PreferencesEntity.SINGLE_ROW_ID)
                .orElseThrow(() -> new IllegalStateException("preferences row is missing"))
                .toDomain();
    }

    @Override
    public Preferences save(Preferences preferences) {
        return repository.save(PreferencesEntity.fromDomain(preferences)).toDomain();
    }
}
