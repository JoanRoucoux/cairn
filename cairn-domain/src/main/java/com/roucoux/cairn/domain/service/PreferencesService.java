package com.roucoux.cairn.domain.service;

import com.roucoux.cairn.domain.model.Language;
import com.roucoux.cairn.domain.model.Preferences;
import com.roucoux.cairn.domain.model.Theme;
import com.roucoux.cairn.domain.port.in.ManagePreferencesUseCase;
import com.roucoux.cairn.domain.port.out.LoadPreferencesPort;
import com.roucoux.cairn.domain.port.out.SavePreferencesPort;

public class PreferencesService implements ManagePreferencesUseCase {

    private final LoadPreferencesPort load;
    private final SavePreferencesPort save;

    public PreferencesService(LoadPreferencesPort load, SavePreferencesPort save) {
        this.load = load;
        this.save = save;
    }

    @Override
    public Preferences get() {
        return load.load();
    }

    @Override
    public Preferences update(Language language, Theme theme) {
        Preferences current = load.load();
        if (language == null && theme == null) {
            return current;
        }
        return save.save(new Preferences(
                language != null ? language : current.language(), theme != null ? theme : current.theme()));
    }
}
