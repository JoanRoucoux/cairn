package com.roucoux.cairn.infrastructure.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.webauthn.api.Bytes;
import org.springframework.security.web.webauthn.api.ImmutablePublicKeyCredentialUserEntity;
import org.springframework.security.web.webauthn.api.PublicKeyCredentialRequestOptions;
import org.springframework.security.web.webauthn.api.PublicKeyCredentialUserEntity;
import org.springframework.security.web.webauthn.authentication.HttpSessionPublicKeyCredentialRequestOptionsRepository;
import org.springframework.security.web.webauthn.authentication.WebAuthnAuthenticationFilter;
import org.springframework.security.web.webauthn.management.WebAuthnRelyingPartyOperations;

class WebAuthnConfigTest {

    private final WebAuthnConfig config = new WebAuthnConfig();
    private final PasswordEncoder passwordEncoder = PasswordEncoderFactories.createDelegatingPasswordEncoder();

    @Test
    void refusesTheDefaultPasswordOutsideTheLocalProfile() {
        MockEnvironment environment = new MockEnvironment();

        assertThatIllegalStateException()
                .isThrownBy(() -> config.userDetailsService(passwordEncoder, environment, "joan", "changeme"))
                .withMessageContaining("app.security.password");
    }

    @Test
    void refusesTheDefaultPasswordUnderAnUnrelatedProfile() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("staging");

        assertThatIllegalStateException()
                .isThrownBy(() -> config.userDetailsService(passwordEncoder, environment, "joan", "changeme"));
    }

    @Test
    void acceptsTheDefaultPasswordUnderTheLocalProfile() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("local");

        assertThat(config.userDetailsService(passwordEncoder, environment, "joan", "changeme"))
                .isNotNull();
    }

    @Test
    void acceptsAnOverriddenPasswordOutsideTheLocalProfile() {
        MockEnvironment environment = new MockEnvironment();

        assertThat(config.userDetailsService(passwordEncoder, environment, "joan", "a-real-password"))
                .isNotNull();
    }

    @Test
    void theAssertionFilterHandsBackAnAuthenticationThatKnowsTheCredentialUsed() throws Exception {
        WebAuthnRelyingPartyOperations operations = mock(WebAuthnRelyingPartyOperations.class);
        UserDetailsService users = mock(UserDetailsService.class);
        PublicKeyCredentialUserEntity owner = ImmutablePublicKeyCredentialUserEntity.builder()
                .name("joan")
                .id(Bytes.random())
                .displayName("Joan Roucoux")
                .build();
        when(operations.authenticate(any())).thenReturn(owner);
        when(users.loadUserByUsername("joan"))
                .thenReturn(
                        User.withUsername("joan").password("x").roles("USER").build());
        WebAuthnAuthenticationFilter filter = WebAuthnConfig.rememberingTheCredential(operations, users)
                .postProcess(new WebAuthnAuthenticationFilter());
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/login/webauthn");
        request.setContentType("application/json");
        request.setContent(("{\"id\":\"aXBob25l\",\"rawId\":\"aXBob25l\",\"type\":\"public-key\","
                        + "\"response\":{\"authenticatorData\":\"AQ\",\"clientDataJSON\":\"e30\","
                        + "\"signature\":\"AQ\",\"userHandle\":\"AQ\"},\"clientExtensionResults\":{}}")
                .getBytes(StandardCharsets.UTF_8));
        MockHttpServletResponse response = new MockHttpServletResponse();
        new HttpSessionPublicKeyCredentialRequestOptionsRepository()
                .save(
                        request,
                        response,
                        PublicKeyCredentialRequestOptions.builder()
                                .challenge(Bytes.random())
                                .rpId("cairn.example")
                                .build());

        Authentication signedIn = filter.attemptAuthentication(request, response);

        assertThat(signedIn)
                .isInstanceOfSatisfying(
                        PasskeyAuthentication.class,
                        passkey -> assertThat(passkey.credentialId()).isEqualTo("aXBob25l"));
    }
}
