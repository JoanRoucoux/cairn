package com.roucoux.cairn.infrastructure.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.authentication.session.ChangeSessionIdAuthenticationStrategy;
import org.springframework.security.web.authentication.session.CompositeSessionAuthenticationStrategy;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.webauthn.api.Bytes;
import org.springframework.security.web.webauthn.api.ImmutablePublicKeyCredentialUserEntity;
import org.springframework.security.web.webauthn.api.PublicKeyCredentialRequestOptions;
import org.springframework.security.web.webauthn.api.PublicKeyCredentialUserEntity;
import org.springframework.security.web.webauthn.authentication.HttpSessionPublicKeyCredentialRequestOptionsRepository;
import org.springframework.security.web.webauthn.authentication.WebAuthnAuthenticationFilter;
import org.springframework.security.web.webauthn.management.WebAuthnRelyingPartyOperations;

class WebAuthnConfigTest {

    private static final String ASSERTION = "{\"id\":\"aXBob25l\",\"rawId\":\"aXBob25l\",\"type\":\"public-key\","
            + "\"response\":{\"authenticatorData\":\"AQ\",\"clientDataJSON\":\"e30\","
            + "\"signature\":\"AQ\",\"userHandle\":\"AQ\"},\"clientExtensionResults\":{}}";

    private final WebAuthnConfig config = new WebAuthnConfig();
    private final PasswordEncoder passwordEncoder = PasswordEncoderFactories.createDelegatingPasswordEncoder();

    @Test
    void refusesTheDefaultPasswordOutsideTheLocalProfile() {
        MockEnvironment environment = new MockEnvironment();

        assertThatIllegalStateException()
                .isThrownBy(() -> config.userDetailsService(passwordEncoder, environment, "alex", "changeme"))
                .withMessageContaining("app.security.password");
    }

    @Test
    void refusesTheDefaultPasswordUnderAnUnrelatedProfile() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("staging");

        assertThatIllegalStateException()
                .isThrownBy(() -> config.userDetailsService(passwordEncoder, environment, "alex", "changeme"));
    }

    @Test
    void acceptsTheDefaultPasswordUnderTheLocalProfile() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("local");

        assertThat(config.userDetailsService(passwordEncoder, environment, "alex", "changeme"))
                .isNotNull();
    }

    @Test
    void acceptsAnOverriddenPasswordOutsideTheLocalProfile() {
        MockEnvironment environment = new MockEnvironment();

        assertThat(config.userDetailsService(passwordEncoder, environment, "alex", "a-real-password"))
                .isNotNull();
    }

    @Test
    void theAssertionFilterHandsBackAnAuthenticationThatKnowsTheCredentialUsed() throws Exception {
        WebAuthnAuthenticationFilter filter = assertionFilter(new ChangeSessionIdAuthenticationStrategy());

        Authentication signedIn = filter.attemptAuthentication(assertionRequest(), new MockHttpServletResponse());

        assertThat(signedIn)
                .isInstanceOfSatisfying(
                        PasskeyAuthentication.class,
                        passkey -> assertThat(passkey.credentialId()).isEqualTo("aXBob25l"));
    }

    @Test
    void theAssertionFilterRotatesTheSessionAndLengthensItThroughTheStrategyItIsGiven() throws Exception {
        WebAuthnAuthenticationFilter filter = assertionFilter(new CompositeSessionAuthenticationStrategy(List.of(
                new ChangeSessionIdAuthenticationStrategy(), new SignedInSessionLifetime(Duration.ofDays(30)))));
        MockHttpServletRequest request = assertionRequest();
        String before = request.getSession().getId();

        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        assertThat(request.getSession().getId()).isNotEqualTo(before);
        assertThat(request.getSession().getMaxInactiveInterval()).isEqualTo(2_592_000);
    }

    @Test
    void theThrottleRunsOnTheClockItIsGiven() {
        Clock fixed = Clock.fixed(Instant.parse("2026-10-18T10:00:00Z"), ZoneOffset.UTC);
        PasswordGuessThrottle throttle = config.passwordGuessThrottle(fixed);
        for (int i = 0; i < PasswordGuessThrottle.FREE_ATTEMPTS; i++) {
            throttle.admit("203.0.113.7");
        }

        assertThat(throttle.admit("203.0.113.7")).isEqualTo(Duration.ofSeconds(1));
    }

    private static WebAuthnAuthenticationFilter assertionFilter(SessionAuthenticationStrategy strategy) {
        WebAuthnRelyingPartyOperations operations = mock(WebAuthnRelyingPartyOperations.class);
        UserDetailsService users = mock(UserDetailsService.class);
        PublicKeyCredentialUserEntity owner = ImmutablePublicKeyCredentialUserEntity.builder()
                .name("alex")
                .id(Bytes.random())
                .displayName("Alex Martin")
                .build();
        when(operations.authenticate(any())).thenReturn(owner);
        when(users.loadUserByUsername("alex"))
                .thenReturn(
                        User.withUsername("alex").password("x").roles("USER").build());
        return WebAuthnConfig.rememberingTheCredential(operations, users, null, () -> strategy)
                .postProcess(new WebAuthnAuthenticationFilter());
    }

    private static MockHttpServletRequest assertionRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/login/webauthn");
        request.setContentType("application/json");
        request.setContent(ASSERTION.getBytes(StandardCharsets.UTF_8));
        new HttpSessionPublicKeyCredentialRequestOptionsRepository()
                .save(
                        request,
                        new MockHttpServletResponse(),
                        PublicKeyCredentialRequestOptions.builder()
                                .challenge(Bytes.random())
                                .rpId("cairn.example")
                                .build());
        return request;
    }
}
