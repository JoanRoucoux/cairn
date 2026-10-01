package com.roucoux.cairn.infrastructure.auth;

import static com.roucoux.cairn.infrastructure.auth.AttestationFixtures.attestationWithAaguid;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullSource;
import org.springframework.security.web.webauthn.api.Bytes;

class PasskeyProvidersTest {

    static Stream<Arguments> knownAuthenticators() {
        return Stream.of(
                Arguments.of("fbfc3007-154e-4ecc-8c0b-6e020557d7bd", "iCloud"),
                Arguments.of("dd4ec289-e01d-41c9-bb89-70fa845d4bf2", "iCloud"),
                Arguments.of("ea9b8d66-4d01-1d21-3ce4-b6b48cb575d4", "Google Password Manager"),
                Arguments.of("08987058-cadc-4b81-b6e1-30de50dcbe96", "Windows Hello"),
                Arguments.of("6028b017-b1d4-4c02-b4b3-afcdafc96bb2", "Windows Hello"),
                Arguments.of("9ddd1817-af5a-4672-a2b9-3e3dd95000a9", "Windows Hello"),
                Arguments.of("bada5566-a7aa-401f-bd96-45619a55120d", "1Password"),
                Arguments.of("d548826e-79b4-db40-a3d8-11116f7e8349", "Bitwarden"),
                Arguments.of("fa2b99dc-9e39-4257-8f92-4a30d23c4118", "Clé de sécurité"),
                Arguments.of("cb69481e-8ff7-4039-93ec-0a2729a154a8", "Clé de sécurité"),
                Arguments.of("a02167b9-ae71-4ac7-9a07-06432ebb6f1c", "Clé de sécurité"));
    }

    @ParameterizedTest
    @MethodSource("knownAuthenticators")
    void namesTheProviderOfAWellKnownAuthenticator(String aaguid, String provider) {
        assertThat(PasskeyProviders.providerOf(attestationWithAaguid(aaguid))).isEqualTo(provider);
    }

    @Test
    void staysSilentForAnAaguidItDoesNotKnow() {
        assertThat(PasskeyProviders.providerOf(attestationWithAaguid(AttestationFixtures.UNKNOWN)))
                .isNull();
    }

    @Test
    void staysSilentForAnAllZeroAaguid() {
        assertThat(PasskeyProviders.providerOf(attestationWithAaguid(AttestationFixtures.NONE)))
                .isNull();
    }

    @Test
    void staysSilentWhenTheAuthenticatorDataCarriesNoCredentialData() {
        assertThat(PasskeyProviders.providerOf(AttestationFixtures.attestationWithoutCredentialData()))
                .isNull();
    }

    @Test
    void staysSilentOnBytesThatAreNotAnAttestationObject() {
        assertThat(PasskeyProviders.providerOf(new Bytes(new byte[] {1, 2, 3}))).isNull();
    }

    @ParameterizedTest
    @NullSource
    void staysSilentWhenNoAttestationWasStored(Bytes attestation) {
        assertThat(PasskeyProviders.providerOf(attestation)).isNull();
    }
}
