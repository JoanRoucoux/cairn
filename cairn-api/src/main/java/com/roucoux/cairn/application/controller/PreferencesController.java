package com.roucoux.cairn.application.controller;

import com.roucoux.cairn.application.mapper.PreferencesRestMapper;
import com.roucoux.cairn.domain.port.in.ManagePreferencesUseCase;
import com.roucoux.cairn.generated.api.PreferencesApi;
import com.roucoux.cairn.generated.model.PreferencesResponse;
import com.roucoux.cairn.generated.model.UpdatePreferencesRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
class PreferencesController implements PreferencesApi {

    private final ManagePreferencesUseCase preferences;
    private final PreferencesRestMapper mapper;

    PreferencesController(ManagePreferencesUseCase preferences, PreferencesRestMapper mapper) {
        this.preferences = preferences;
        this.mapper = mapper;
    }

    @Override
    public ResponseEntity<PreferencesResponse> getPreferences() {
        return ResponseEntity.ok(mapper.toResponse(preferences.get()));
    }

    @Override
    public ResponseEntity<PreferencesResponse> updatePreferences(UpdatePreferencesRequest request) {
        return ResponseEntity.ok(mapper.toResponse(
                preferences.update(mapper.toLanguage(request.getLanguage()), mapper.toTheme(request.getTheme()))));
    }
}
