package com.roucoux.cairn.application.controller;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.roucoux.cairn.application.mapper.SessionRestMapper;
import com.roucoux.cairn.infrastructure.auth.PasskeyAuthentication;
import com.roucoux.cairn.infrastructure.auth.WebAuthnConfig;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.web.webauthn.api.Bytes;
import org.springframework.security.web.webauthn.api.CredentialRecord;
import org.springframework.security.web.webauthn.api.ImmutablePublicKeyCredentialUserEntity;
import org.springframework.security.web.webauthn.api.PublicKeyCredentialRequestOptions;
import org.springframework.security.web.webauthn.api.PublicKeyCredentialUserEntity;
import org.springframework.security.web.webauthn.authentication.HttpSessionPublicKeyCredentialRequestOptionsRepository;
import org.springframework.security.web.webauthn.management.PublicKeyCredentialUserEntityRepository;
import org.springframework.security.web.webauthn.management.UserCredentialRepository;
import org.springframework.security.web.webauthn.management.WebAuthnRelyingPartyOperations;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@TestPropertySource(properties = "app.security.password=test-password")
@WebMvcTest(SessionController.class)
@Import({WebAuthnConfig.class, SessionRestMapper.class})
class SessionControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    PublicKeyCredentialUserEntityRepository userEntities;

    @MockitoBean
    UserCredentialRepository credentials;

    @MockitoBean
    FindByIndexNameSessionRepository<Session> sessions;

    @MockitoBean
    WebAuthnRelyingPartyOperations relyingParty;

    private void givenOwner(String username, String displayName) {
        PublicKeyCredentialUserEntity owner = org.mockito.Mockito.mock(PublicKeyCredentialUserEntity.class);
        when(owner.getId()).thenReturn(new Bytes(username.getBytes()));
        when(owner.getDisplayName()).thenReturn(displayName);
        when(userEntities.findByUsername(username)).thenReturn(owner);
    }

    private void givenPasskeys(CredentialRecord... records) {
        when(credentials.findByUserId(any())).thenReturn(List.of(records));
    }

    private CredentialRecord aCredential(String credentialId, String label) {
        CredentialRecord credential = org.mockito.Mockito.mock(CredentialRecord.class);
        when(credential.getCredentialId()).thenReturn(Bytes.fromBase64(credentialId));
        when(credential.getLabel()).thenReturn(label);
        when(credential.getCreated()).thenReturn(Instant.parse("2026-01-01T00:00:00Z"));
        when(credential.getLastUsed()).thenReturn(Instant.parse("2026-01-02T00:00:00Z"));
        return credential;
    }

    private CredentialRecord aNeverUsedCredential(String credentialId, String label) {
        CredentialRecord credential = org.mockito.Mockito.mock(CredentialRecord.class);
        when(credential.getCredentialId()).thenReturn(Bytes.fromBase64(credentialId));
        when(credential.getLabel()).thenReturn(label);
        when(credential.getCreated()).thenReturn(Instant.parse("2026-01-01T00:00:00Z"));
        when(credential.getLastUsed()).thenReturn(null);
        return credential;
    }

    @Test
    void returnsTheOwnerAndTheirPasskeys() throws Exception {
        givenOwner("joan", "Joan Roucoux");
        givenPasskeys(aCredential("aXBob25l", "iPhone de Joan"), aCredential("bWFj", "MacBook"));

        mockMvc.perform(get("/session").with(user("joan")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("Joan Roucoux"))
                .andExpect(jsonPath("$.initials").value("JR"))
                .andExpect(jsonPath("$.passkeys.length()").value(2))
                .andExpect(jsonPath("$.passkeys[0].label").value("iPhone de Joan"));
    }

    @Test
    void reportsAPasswordSessionWithNoCurrentPasskey() throws Exception {
        givenOwner("joan", "Joan Roucoux");
        givenPasskeys(aCredential("aXBob25l", "iPhone de Joan"));

        mockMvc.perform(get("/session").with(user("joan")))
                .andExpect(jsonPath("$.username").value("joan"))
                .andExpect(jsonPath("$.signInMethod").value("PASSWORD"))
                .andExpect(jsonPath("$.passkeys[0].current").value(false))
                .andExpect(jsonPath("$.passkeys[0].provider").doesNotExist());
    }

    @Test
    void reportsAPasskeySessionAndTheCredentialThatOpenedIt() throws Exception {
        givenOwner("joan", "Joan Roucoux");
        givenPasskeys(aCredential("aXBob25l", "iPhone de Joan"), aCredential("bWFj", "MacBook"));
        PublicKeyCredentialUserEntity principal = ImmutablePublicKeyCredentialUserEntity.builder()
                .name("joan")
                .id(Bytes.random())
                .displayName("Joan Roucoux")
                .build();

        mockMvc.perform(get("/session").with(authentication(new PasskeyAuthentication(principal, List.of(), "bWFj"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("joan"))
                .andExpect(jsonPath("$.signInMethod").value("PASSKEY"))
                .andExpect(jsonPath("$.passkeys[0].current").value(false))
                .andExpect(jsonPath("$.passkeys[1].current").value(true));
    }

    @Test
    void aPasskeySignInThroughTheSecurityFilterChainOpensASessionThatKnowsItsCredential() throws Exception {
        PublicKeyCredentialUserEntity principal = ImmutablePublicKeyCredentialUserEntity.builder()
                .name("joan")
                .id(Bytes.random())
                .displayName("Joan Roucoux")
                .build();
        givenOwner("joan", "Joan Roucoux");
        givenPasskeys(aCredential("aXBob25l", "iPhone de Joan"), aCredential("bWFj", "MacBook"));
        when(relyingParty.authenticate(any())).thenReturn(principal);
        MockHttpSession session = new MockHttpSession();
        MockHttpServletRequest holder = new MockHttpServletRequest();
        holder.setSession(session);
        new HttpSessionPublicKeyCredentialRequestOptionsRepository()
                .save(
                        holder,
                        new MockHttpServletResponse(),
                        PublicKeyCredentialRequestOptions.builder()
                                .challenge(Bytes.random())
                                .rpId("localhost")
                                .build());

        mockMvc.perform(post("/login/webauthn")
                        .session(session)
                        .with(csrf())
                        .contentType("application/json")
                        .content("{\"id\":\"bWFj\",\"rawId\":\"bWFj\",\"type\":\"public-key\","
                                + "\"response\":{\"authenticatorData\":\"AQ\",\"clientDataJSON\":\"e30\","
                                + "\"signature\":\"AQ\",\"userHandle\":\"AQ\"},\"clientExtensionResults\":{}}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/session").session(session))
                .andExpect(jsonPath("$.signInMethod").value("PASSKEY"))
                .andExpect(jsonPath("$.passkeys[0].current").value(false))
                .andExpect(jsonPath("$.passkeys[1].current").value(true));
    }

    @Test
    void reportsAPasskeyThatWasNeverUsedAsNullNotAsAnEpoch() throws Exception {
        givenOwner("joan", "Joan Roucoux");
        givenPasskeys(aNeverUsedCredential("aXBob25l", "iPhone de Joan"));

        mockMvc.perform(get("/session").with(user("joan")))
                .andExpect(jsonPath("$.passkeys[0].lastUsedAt").doesNotExist());
    }

    @Test
    void refusesAnUnauthenticatedCall() throws Exception {
        mockMvc.perform(get("/session")).andExpect(status().isUnauthorized());
    }

    @Test
    void returnsAnEmptySessionWhenNoPasskeyWasRegisteredYet() throws Exception {
        when(userEntities.findByUsername("joan")).thenReturn(null);

        mockMvc.perform(get("/session").with(user("joan")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("joan"))
                .andExpect(jsonPath("$.passkeys.length()").value(0));
    }

    @Test
    void revokingAPasskeyReturnsNotFoundWhenNoPasskeyWasRegisteredYet() throws Exception {
        when(userEntities.findByUsername("joan")).thenReturn(null);

        mockMvc.perform(delete("/session/passkeys/{id}", "aXBob25l")
                        .with(user("joan"))
                        .with(csrf()))
                .andExpect(status().isNotFound());

        verify(credentials, never()).delete(any());
    }

    @Test
    void revokesAPasskeyWhenAnotherOneRemains() throws Exception {
        givenOwner("joan", "Joan Roucoux");
        givenPasskeys(aCredential("aXBob25l", "iPhone de Joan"), aCredential("bWFj", "MacBook"));

        mockMvc.perform(delete("/session/passkeys/{id}", "bWFj")
                        .with(user("joan"))
                        .with(csrf()))
                .andExpect(status().isNoContent());

        verify(credentials).delete(any());
    }

    @Test
    void refusesToRevokeTheLastPasskey() throws Exception {
        givenOwner("joan", "Joan Roucoux");
        givenPasskeys(aCredential("aXBob25l", "iPhone de Joan"));

        mockMvc.perform(delete("/session/passkeys/{id}", "aXBob25l")
                        .with(user("joan"))
                        .with(csrf()))
                .andExpect(status().isConflict());

        verify(credentials, never()).delete(any());
    }

    @Test
    void revokingAPasskeySignsOutEveryOtherSessionOfTheOwner() throws Exception {
        givenOwner("joan", "Joan Roucoux");
        givenPasskeys(aCredential("aXBob25l", "iPhone de Joan"), aCredential("bWFj", "MacBook"));
        when(sessions.findByPrincipalName("joan"))
                .thenReturn(Map.of(
                        "current-session-id", org.mockito.Mockito.mock(Session.class),
                        "other-session-id", org.mockito.Mockito.mock(Session.class)));

        mockMvc.perform(delete("/session/passkeys/{id}", "bWFj")
                        .with(user("joan"))
                        .with(csrf())
                        .session(new MockHttpSession(null, "current-session-id")))
                .andExpect(status().isNoContent());

        verify(sessions).deleteById("other-session-id");
        verify(sessions, never()).deleteById("current-session-id");
    }

    @Test
    void leavesTheCredentialInPlaceWhenSigningOutOtherSessionsFails() {
        givenOwner("joan", "Joan Roucoux");
        givenPasskeys(aCredential("aXBob25l", "iPhone de Joan"), aCredential("bWFj", "MacBook"));
        when(sessions.findByPrincipalName("joan")).thenThrow(new RuntimeException("session store unavailable"));

        assertThatThrownBy(() -> mockMvc.perform(delete("/session/passkeys/{id}", "bWFj")
                        .with(user("joan"))
                        .with(csrf())))
                .isInstanceOf(Exception.class);

        verify(credentials, never()).delete(any());
    }

    @Test
    void refusesToRevokeAPasskeyThatBelongsToNobodyHere() throws Exception {
        givenOwner("joan", "Joan Roucoux");
        givenPasskeys(aCredential("aXBob25l", "iPhone de Joan"), aCredential("bWFj", "MacBook"));

        mockMvc.perform(delete("/session/passkeys/{id}", "aW5jb25udQ")
                        .with(user("joan"))
                        .with(csrf()))
                .andExpect(status().isNotFound());

        verify(credentials, never()).delete(any());
    }
}
