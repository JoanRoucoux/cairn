package com.roucoux.cairn.infrastructure.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.web.webauthn.api.Bytes;
import org.springframework.security.web.webauthn.api.ImmutablePublicKeyCredentialUserEntity;
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
