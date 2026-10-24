package com.roucoux.cairn.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.roucoux.cairn.domain.model.Language;
import com.roucoux.cairn.domain.model.Preferences;
import com.roucoux.cairn.domain.model.Theme;
import com.roucoux.cairn.domain.port.in.ManagePreferencesUseCase;
import com.roucoux.cairn.domain.port.out.LoadPreferencesPort;
import com.roucoux.cairn.domain.port.out.SavePreferencesPort;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class PreferencesDomainConfigTest {

    @Test
    void wiresThePreferencesSliceSoAnUpdateIsSaved() {
        Preferences stored = new Preferences(Language.FR, Theme.SYSTEM);
        List<Preferences> saved = new ArrayList<>();
        LoadPreferencesPort load = () -> stored;
        SavePreferencesPort save = preferences -> {
            saved.add(preferences);
            return preferences;
        };

        ManagePreferencesUseCase useCase = new PreferencesDomainConfig().managePreferencesUseCase(load, save);
        Preferences updated = useCase.update(Language.EN, null);

        assertThat(updated).isEqualTo(new Preferences(Language.EN, Theme.SYSTEM));
        assertThat(saved).containsExactly(new Preferences(Language.EN, Theme.SYSTEM));
    }
}
