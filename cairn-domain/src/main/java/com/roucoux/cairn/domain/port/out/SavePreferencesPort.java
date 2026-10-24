package com.roucoux.cairn.domain.port.out;

import com.roucoux.cairn.domain.model.Preferences;

public interface SavePreferencesPort {

    Preferences save(Preferences preferences);
}
