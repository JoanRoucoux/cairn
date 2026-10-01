package com.roucoux.cairn.infrastructure.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.webauthn.api.AuthenticatorAssertionResponse;
import org.springframework.security.web.webauthn.api.Bytes;
import org.springframework.security.web.webauthn.api.ImmutablePublicKeyCredentialUserEntity;
import org.springframework.security.web.webauthn.api.PublicKeyCredential;
import org.springframework.security.web.webauthn.api.PublicKeyCredentialRequestOptions;
import org.springframework.security.web.webauthn.api.PublicKeyCredentialUserEntity;
import org.springframework.security.web.webauthn.authentication.WebAuthnAuthentication;
import org.springframework.security.web.webauthn.authentication.WebAuthnAuthenticationProvider;
import org.springframework.security.web.webauthn.authentication.WebAuthnAuthenticationRequestToken;
import org.springframework.security.web.webauthn.management.RelyingPartyAuthenticationRequest;
import org.springframework.security.web.webauthn.management.WebAuthnRelyingPartyOperations;

class PasskeyAuthenticationProviderTest {

    private final WebAuthnRelyingPartyOperations operations = mock(WebAuthnRelyingPartyOperations.class);
    private final UserDetailsService users = mock(UserDetailsService.class);
    private final PasskeyAuthenticationProvider provider =
            new PasskeyAuthenticationProvider(new WebAuthnAuthenticationProvider(operations, users));

    private WebAuthnAuthenticationRequestToken requestFor(Bytes rawId) {
        PublicKeyCredential<AuthenticatorAssertionResponse> credential = mock(PublicKeyCredential.class);
        when(credential.getRawId()).thenReturn(rawId);
        RelyingPartyAuthenticationRequest request = new RelyingPartyAuthenticationRequest(
                PublicKeyCredentialRequestOptions.builder()
                        .challenge(Bytes.random())
                        .rpId("cairn.example")
                        .build(),
                credential);
        return new WebAuthnAuthenticationRequestToken(request);
    }

    @Test
    void remembersTheCredentialThatSignedTheOwnerIn() {
        PublicKeyCredentialUserEntity owner = ImmutablePublicKeyCredentialUserEntity.builder()
                .name("joan")
                .id(Bytes.random())
                .displayName("Joan Roucoux")
                .build();
        Bytes rawId = Bytes.fromBase64("aXBob25l");
        WebAuthnAuthenticationRequestToken token = requestFor(rawId);
        when(operations.authenticate(token.getWebAuthnRequest())).thenReturn(owner);
        when(users.loadUserByUsername("joan"))
                .thenReturn(
                        User.withUsername("joan").password("x").roles("USER").build());

        var signedIn = provider.authenticate(token);

        assertThat(signedIn).isInstanceOf(PasskeyAuthentication.class).isInstanceOf(WebAuthnAuthentication.class);
        assertThat(((PasskeyAuthentication) signedIn).credentialId()).isEqualTo(rawId.toBase64UrlString());
        assertThat(signedIn.getName()).isEqualTo("joan");
        assertThat(signedIn.isAuthenticated()).isTrue();
        assertThat(signedIn.getAuthorities().stream().map(a -> a.getAuthority()))
                .contains("ROLE_USER");
    }

    @Test
    void handlesTheSameRequestsAsTheProviderItWraps() {
        assertThat(provider.supports(WebAuthnAuthenticationRequestToken.class)).isTrue();
        assertThat(provider.supports(UsernamePasswordAuthenticationToken.class)).isFalse();
    }
}
