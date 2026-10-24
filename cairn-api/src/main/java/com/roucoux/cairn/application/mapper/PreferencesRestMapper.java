package com.roucoux.cairn.application.mapper;

import com.roucoux.cairn.domain.model.Language;
import com.roucoux.cairn.domain.model.Preferences;
import com.roucoux.cairn.domain.model.Theme;
import com.roucoux.cairn.generated.model.PreferencesResponse;
import com.roucoux.cairn.generated.model.UpdatePreferencesRequest;
import java.util.Locale;
import org.springframework.stereotype.Component;

@Component
public class PreferencesRestMapper {

    public PreferencesResponse toResponse(Preferences preferences) {
        return new PreferencesResponse(
                PreferencesResponse.LanguageEnum.fromValue(
                        preferences.language().name().toLowerCase(Locale.ROOT)),
                PreferencesResponse.ThemeEnum.fromValue(
                        preferences.theme().name().toLowerCase(Locale.ROOT)));
    }

    public Language toLanguage(UpdatePreferencesRequest.LanguageEnum language) {
        return language == null ? null : Language.valueOf(language.name());
    }

    public Theme toTheme(UpdatePreferencesRequest.ThemeEnum theme) {
        return theme == null ? null : Theme.valueOf(theme.name());
    }
}
