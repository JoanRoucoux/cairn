package com.roucoux.cairn.infrastructure.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;

/** No Spring context: the bean-wiring method is called directly, per the module's convention. */
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
}
