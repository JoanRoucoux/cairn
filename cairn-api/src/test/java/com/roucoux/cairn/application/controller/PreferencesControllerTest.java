package com.roucoux.cairn.application.controller;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.roucoux.cairn.application.mapper.PreferencesRestMapper;
import com.roucoux.cairn.domain.model.Language;
import com.roucoux.cairn.domain.model.Preferences;
import com.roucoux.cairn.domain.model.Theme;
import com.roucoux.cairn.domain.port.in.ManagePreferencesUseCase;
import com.roucoux.cairn.infrastructure.auth.WebAuthnConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.web.webauthn.management.PublicKeyCredentialUserEntityRepository;
import org.springframework.security.web.webauthn.management.UserCredentialRepository;
import org.springframework.security.web.webauthn.management.WebAuthnRelyingPartyOperations;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@TestPropertySource(properties = {"app.security.password=test-password", "app.security.username=alex"})
@WebMvcTest(PreferencesController.class)
@Import({WebAuthnConfig.class, PreferencesRestMapper.class, TestClockConfig.class})
class PreferencesControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    ManagePreferencesUseCase preferences;

    @MockitoBean
    PublicKeyCredentialUserEntityRepository userEntities;

    @MockitoBean
    UserCredentialRepository credentials;

    @MockitoBean
    WebAuthnRelyingPartyOperations relyingParty;

    @Test
    void getAnswersTheStoredPreferences() throws Exception {
        when(preferences.get()).thenReturn(new Preferences(Language.FR, Theme.SYSTEM));

        mockMvc.perform(get("/session/preferences").with(user("alex")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.language").value("fr"))
                .andExpect(jsonPath("$.theme").value("system"));
    }

    @Test
    void patchWithOnlyTheLanguagePassesANullTheme() throws Exception {
        when(preferences.update(Language.EN, null)).thenReturn(new Preferences(Language.EN, Theme.SYSTEM));

        mockMvc.perform(patch("/session/preferences")
                        .with(user("alex"))
                        .with(csrf())
                        .contentType("application/json")
                        .content("{\"language\":\"en\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.language").value("en"))
                .andExpect(jsonPath("$.theme").value("system"));

        verify(preferences).update(Language.EN, null);
    }

    @Test
    void patchWithAnEmptyBodyChangesNothing() throws Exception {
        when(preferences.update(null, null)).thenReturn(new Preferences(Language.FR, Theme.DARK));

        mockMvc.perform(patch("/session/preferences")
                        .with(user("alex"))
                        .with(csrf())
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.theme").value("dark"));

        verify(preferences).update(null, null);
    }

    @Test
    void patchWithAnUnknownLanguageIs400() throws Exception {
        mockMvc.perform(patch("/session/preferences")
                        .with(user("alex"))
                        .with(csrf())
                        .contentType("application/json")
                        .content("{\"language\":\"de\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void patchWithoutCsrfIs403() throws Exception {
        mockMvc.perform(patch("/session/preferences")
                        .with(user("alex"))
                        .contentType("application/json")
                        .content("{\"theme\":\"dark\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void getWithoutSessionIs401() throws Exception {
        mockMvc.perform(get("/session/preferences")).andExpect(status().isUnauthorized());
    }
}
