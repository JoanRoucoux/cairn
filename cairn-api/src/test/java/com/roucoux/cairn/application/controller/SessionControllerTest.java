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

@TestPropertySource(properties = {"app.security.password=test-password", "app.security.username=alex"})
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
    void returnsTheOwnerWithoutTheirPasskeys() throws Exception {
        givenOwner("alex", "Alex Martin");
        givenPasskeys(aCredential("aXBob25l", "Alex's iPhone"));

        mockMvc.perform(get("/session").with(user("alex")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("Alex Martin"))
                .andExpect(jsonPath("$.initials").value("AM"))
                .andExpect(jsonPath("$.username").value("alex"))
                .andExpect(jsonPath("$.signInMethod").value("PASSWORD"))
                .andExpect(jsonPath("$.passkeys").doesNotExist());
    }

    @Test
    void returnsTheOwnerNamedAfterTheirUsernameWhenNoPasskeyWasRegisteredYet() throws Exception {
        when(userEntities.findByUsername("alex")).thenReturn(null);

        mockMvc.perform(get("/session").with(user("alex")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("alex"));
    }

    @Test
    void reportsAPasskeySessionOpenedByThePasskeyAuthentication() throws Exception {
        PublicKeyCredentialUserEntity principal = ImmutablePublicKeyCredentialUserEntity.builder()
                .name("alex")
                .id(Bytes.random())
                .displayName("Alex Martin")
                .build();

        mockMvc.perform(get("/session").with(authentication(new PasskeyAuthentication(principal, List.of(), "bWFj"))))
                .andExpect(jsonPath("$.username").value("alex"))
                .andExpect(jsonPath("$.signInMethod").value("PASSKEY"));
    }

    @Test
    void listsThePasskeysOfTheOwner() throws Exception {
        givenOwner("alex", "Alex Martin");
        givenPasskeys(aCredential("aXBob25l", "Alex's iPhone"), aCredential("bWFj", "MacBook"));

        mockMvc.perform(get("/session/passkeys").with(user("alex")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].label").value("Alex's iPhone"))
                .andExpect(jsonPath("$[1].label").value("MacBook"));
    }

    @Test
    void flagsNoPasskeyAsCurrentWhenThePasswordOpenedTheSession() throws Exception {
        givenOwner("alex", "Alex Martin");
        givenPasskeys(aCredential("aXBob25l", "Alex's iPhone"), aCredential("bWFj", "MacBook"));

        mockMvc.perform(get("/session/passkeys").with(user("alex")))
                .andExpect(jsonPath("$[0].current").value(false))
                .andExpect(jsonPath("$[1].current").value(false))
                .andExpect(jsonPath("$[0].provider").doesNotExist());
    }

    @Test
    void flagsThePasskeyThatOpenedTheSessionAsCurrent() throws Exception {
        givenOwner("alex", "Alex Martin");
        givenPasskeys(aCredential("aXBob25l", "Alex's iPhone"), aCredential("bWFj", "MacBook"));
        PublicKeyCredentialUserEntity principal = ImmutablePublicKeyCredentialUserEntity.builder()
                .name("alex")
                .id(Bytes.random())
                .displayName("Alex Martin")
                .build();

        mockMvc.perform(get("/session/passkeys")
                        .with(authentication(new PasskeyAuthentication(principal, List.of(), "bWFj"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].current").value(false))
                .andExpect(jsonPath("$[1].current").value(true));
    }

    @Test
    void aPasskeySignInThroughTheSecurityFilterChainOpensASessionThatKnowsItsCredential() throws Exception {
        PublicKeyCredentialUserEntity principal = ImmutablePublicKeyCredentialUserEntity.builder()
                .name("alex")
                .id(Bytes.random())
                .displayName("Alex Martin")
                .build();
        givenOwner("alex", "Alex Martin");
        givenPasskeys(aCredential("aXBob25l", "Alex's iPhone"), aCredential("bWFj", "MacBook"));
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
                .andExpect(jsonPath("$.signInMethod").value("PASSKEY"));
        mockMvc.perform(get("/session/passkeys").session(session))
                .andExpect(jsonPath("$[0].current").value(false))
                .andExpect(jsonPath("$[1].current").value(true));
    }

    @Test
    void reportsAPasskeyThatWasNeverUsedAsNullNotAsAnEpoch() throws Exception {
        givenOwner("alex", "Alex Martin");
        givenPasskeys(aNeverUsedCredential("aXBob25l", "Alex's iPhone"));

        mockMvc.perform(get("/session/passkeys").with(user("alex")))
                .andExpect(jsonPath("$[0].lastUsedAt").doesNotExist());
    }

    @Test
    void refusesAnUnauthenticatedCall() throws Exception {
        mockMvc.perform(get("/session")).andExpect(status().isUnauthorized());
    }

    @Test
    void refusesAnUnauthenticatedPasskeyListing() throws Exception {
        mockMvc.perform(get("/session/passkeys")).andExpect(status().isUnauthorized());
    }

    @Test
    void listsNoPasskeyWhenNoneWasRegisteredYet() throws Exception {
        when(userEntities.findByUsername("alex")).thenReturn(null);

        mockMvc.perform(get("/session/passkeys").with(user("alex")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void revokingAPasskeyReturnsNotFoundWhenNoPasskeyWasRegisteredYet() throws Exception {
        when(userEntities.findByUsername("alex")).thenReturn(null);

        mockMvc.perform(delete("/session/passkeys/{id}", "aXBob25l")
                        .with(user("alex"))
                        .with(csrf()))
                .andExpect(status().isNotFound());

        verify(credentials, never()).delete(any());
    }

    @Test
    void revokesAPasskeyWhenAnotherOneRemains() throws Exception {
        givenOwner("alex", "Alex Martin");
        givenPasskeys(aCredential("aXBob25l", "Alex's iPhone"), aCredential("bWFj", "MacBook"));

        mockMvc.perform(delete("/session/passkeys/{id}", "bWFj")
                        .with(user("alex"))
                        .with(csrf()))
                .andExpect(status().isNoContent());

        verify(credentials).delete(any());
    }

    @Test
    void refusesToRevokeTheLastPasskey() throws Exception {
        givenOwner("alex", "Alex Martin");
        givenPasskeys(aCredential("aXBob25l", "Alex's iPhone"));

        mockMvc.perform(delete("/session/passkeys/{id}", "aXBob25l")
                        .with(user("alex"))
                        .with(csrf()))
                .andExpect(status().isConflict());

        verify(credentials, never()).delete(any());
    }

    @Test
    void revokingAPasskeySignsOutEveryOtherSessionOfTheOwner() throws Exception {
        givenOwner("alex", "Alex Martin");
        givenPasskeys(aCredential("aXBob25l", "Alex's iPhone"), aCredential("bWFj", "MacBook"));
        when(sessions.findByPrincipalName("alex"))
                .thenReturn(Map.of(
                        "current-session-id", org.mockito.Mockito.mock(Session.class),
                        "other-session-id", org.mockito.Mockito.mock(Session.class)));

        mockMvc.perform(delete("/session/passkeys/{id}", "bWFj")
                        .with(user("alex"))
                        .with(csrf())
                        .session(new MockHttpSession(null, "current-session-id")))
                .andExpect(status().isNoContent());

        verify(sessions).deleteById("other-session-id");
        verify(sessions, never()).deleteById("current-session-id");
    }

    @Test
    void leavesTheCredentialInPlaceWhenSigningOutOtherSessionsFails() {
        givenOwner("alex", "Alex Martin");
        givenPasskeys(aCredential("aXBob25l", "Alex's iPhone"), aCredential("bWFj", "MacBook"));
        when(sessions.findByPrincipalName("alex")).thenThrow(new RuntimeException("session store unavailable"));

        assertThatThrownBy(() -> mockMvc.perform(delete("/session/passkeys/{id}", "bWFj")
                        .with(user("alex"))
                        .with(csrf())))
                .isInstanceOf(Exception.class);

        verify(credentials, never()).delete(any());
    }

    @Test
    void refusesToRevokeAPasskeyThatBelongsToNobodyHere() throws Exception {
        givenOwner("alex", "Alex Martin");
        givenPasskeys(aCredential("aXBob25l", "Alex's iPhone"), aCredential("bWFj", "MacBook"));

        mockMvc.perform(delete("/session/passkeys/{id}", "aW5jb25udQ")
                        .with(user("alex"))
                        .with(csrf()))
                .andExpect(status().isNotFound());

        verify(credentials, never()).delete(any());
    }
}
