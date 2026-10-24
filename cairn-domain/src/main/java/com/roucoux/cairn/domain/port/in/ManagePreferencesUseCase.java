package com.roucoux.cairn.domain.port.in;

import com.roucoux.cairn.domain.model.Language;
import com.roucoux.cairn.domain.model.Preferences;
import com.roucoux.cairn.domain.model.Theme;

public interface ManagePreferencesUseCase {

    Preferences get();

    Preferences update(Language language, Theme theme);
}
