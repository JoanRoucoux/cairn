package com.roucoux.cairn.domain.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.roucoux.cairn.domain.model.Language;
import com.roucoux.cairn.domain.model.Preferences;
import com.roucoux.cairn.domain.model.Theme;
import com.roucoux.cairn.domain.port.out.LoadPreferencesPort;
import com.roucoux.cairn.domain.port.out.SavePreferencesPort;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class PreferencesServiceTest {

    @Test
    void getReturnsTheStoredPreferences() {
        Fixture fixture = new Fixture(new Preferences(Language.FR, Theme.DARK));

        assertThat(fixture.service().get()).isEqualTo(new Preferences(Language.FR, Theme.DARK));
    }

    @Test
    void updateWithOnlyALanguageKeepsTheStoredTheme() {
        Fixture fixture = new Fixture(new Preferences(Language.FR, Theme.DARK));

        Preferences updated = fixture.service().update(Language.EN, null);

        assertThat(updated).isEqualTo(new Preferences(Language.EN, Theme.DARK));
        assertThat(fixture.saved).containsExactly(new Preferences(Language.EN, Theme.DARK));
    }

    @Test
    void updateWithOnlyAThemeKeepsTheStoredLanguage() {
        Fixture fixture = new Fixture(new Preferences(Language.FR, Theme.DARK));

        Preferences updated = fixture.service().update(null, Theme.LIGHT);

        assertThat(updated).isEqualTo(new Preferences(Language.FR, Theme.LIGHT));
        assertThat(fixture.saved).containsExactly(new Preferences(Language.FR, Theme.LIGHT));
    }

    @Test
    void updateWithNothingSavesNothingAndReturnsTheStoredPreferences() {
        Fixture fixture = new Fixture(new Preferences(Language.FR, Theme.DARK));

        Preferences updated = fixture.service().update(null, null);

        assertThat(updated).isEqualTo(new Preferences(Language.FR, Theme.DARK));
        assertThat(fixture.saved).isEmpty();
    }

    private static final class Fixture {

        final Preferences stored;
        final List<Preferences> saved = new ArrayList<>();

        Fixture(Preferences stored) {
            this.stored = stored;
        }

        PreferencesService service() {
            LoadPreferencesPort load = () -> stored;
            SavePreferencesPort save = preferences -> {
                saved.add(preferences);
                return preferences;
            };
            return new PreferencesService(load, save);
        }
    }
}
