package com.roucoux.cairn.infrastructure.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.web.webauthn.api.Bytes;
import org.springframework.security.web.webauthn.api.ImmutablePublicKeyCredentialUserEntity;
import org.springframework.security.web.webauthn.api.PublicKeyCredentialCreationOptions;
import org.springframework.security.web.webauthn.api.PublicKeyCredentialParameters;
import org.springframework.security.web.webauthn.api.PublicKeyCredentialRequestOptions;
import org.springframework.security.web.webauthn.api.PublicKeyCredentialRpEntity;
import org.springframework.security.web.webauthn.authentication.WebAuthnAuthentication;

class SessionSerializationTest {

    @Test
    void aPasskeyAuthenticationSurvivesTheTripThroughTheSessionTable() throws Exception {
        WebAuthnAuthentication signedIn = new WebAuthnAuthentication(
                ImmutablePublicKeyCredentialUserEntity.builder()
                        .name("joan")
                        .id(Bytes.random())
                        .displayName("Joan")
                        .build(),
                List.of(new SimpleGrantedAuthority("ROLE_USER")));

        WebAuthnAuthentication restored = (WebAuthnAuthentication) roundTrip(signedIn);

        assertThat(restored.getName()).isEqualTo("joan");
        assertThat(restored.isAuthenticated()).isTrue();
    }

    // HttpSessionPublicKeyCredentialRequestOptionsRepository holds exactly this between the
    // passkey assertion's options call and its verification (see WebAuthnConfig's Javadoc).
    @Test
    void aRequestOptionsChallengeSurvivesTheTripThroughTheSessionTable() throws Exception {
        PublicKeyCredentialRequestOptions options = PublicKeyCredentialRequestOptions.builder()
                .challenge(Bytes.random())
                .timeout(Duration.ofMinutes(5))
                .rpId("cairn.example")
                .build();

        PublicKeyCredentialRequestOptions restored = (PublicKeyCredentialRequestOptions) roundTrip(options);

        assertThat(restored.getRpId()).isEqualTo("cairn.example");
    }

    // HttpSessionPublicKeyCredentialCreationOptionsRepository holds exactly this between the
    // passkey registration's options call and its verification.
    @Test
    void aCreationOptionsChallengeSurvivesTheTripThroughTheSessionTable() throws Exception {
        PublicKeyCredentialCreationOptions options = PublicKeyCredentialCreationOptions.builder()
                .rp(PublicKeyCredentialRpEntity.builder()
                        .name("Cairn")
                        .id("cairn.example")
                        .build())
                .user(ImmutablePublicKeyCredentialUserEntity.builder()
                        .name("joan")
                        .id(Bytes.random())
                        .displayName("Joan")
                        .build())
                .challenge(Bytes.random())
                .pubKeyCredParams(PublicKeyCredentialParameters.ES256)
                .build();

        PublicKeyCredentialCreationOptions restored = (PublicKeyCredentialCreationOptions) roundTrip(options);

        assertThat(restored.getRp().getId()).isEqualTo("cairn.example");
    }

    private static Object roundTrip(Object value) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream out = new ObjectOutputStream(bytes)) {
            out.writeObject(value);
        }
        try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            return in.readObject();
        }
    }
}
