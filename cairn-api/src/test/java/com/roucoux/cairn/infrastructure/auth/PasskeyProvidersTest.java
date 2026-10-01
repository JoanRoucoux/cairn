package com.roucoux.cairn.infrastructure.auth;

import static com.roucoux.cairn.infrastructure.auth.AttestationFixtures.attestationWithAaguid;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Base64;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullSource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.security.web.webauthn.api.Bytes;

class PasskeyProvidersTest {

    static Stream<Arguments> everyKnownAuthenticator() {
        return Stream.of(
                Arguments.of("9eb7eabc-9db5-49a1-b6c3-555a802093f4", PasskeyProvider.SECURITY_KEY),
                Arguments.of("c5ef55ff-ad9a-4b9f-b580-adebafe026d0", PasskeyProvider.SECURITY_KEY),
                Arguments.of("fa2b99dc-9e39-4257-8f92-4a30d23c4118", PasskeyProvider.SECURITY_KEY),
                Arguments.of("08987058-cadc-4b81-b6e1-30de50dcbe96", PasskeyProvider.WINDOWS_HELLO),
                Arguments.of("0a357157-9b18-4c8a-920e-d156e972b2f8", PasskeyProvider.SECURITY_KEY),
                Arguments.of("7dab85a5-d16d-4eaf-a7ef-4c1385b151c5", PasskeyProvider.SECURITY_KEY),
                Arguments.of("a02167b9-ae71-4ac7-9a07-06432ebb6f1c", PasskeyProvider.SECURITY_KEY),
                Arguments.of("2fc0579f-8113-47ea-b116-bb5a8db9202a", PasskeyProvider.SECURITY_KEY),
                Arguments.of("9ddd1817-af5a-4672-a2b9-3e3dd95000a9", PasskeyProvider.WINDOWS_HELLO),
                Arguments.of("03012cb7-4fb2-42e7-9e8d-a81f10e2a5e9", PasskeyProvider.SECURITY_KEY),
                Arguments.of("f4ce5fc0-57d3-46f5-a736-efb7d5bc63b5", PasskeyProvider.SECURITY_KEY),
                Arguments.of("d7781e5d-e353-46aa-afe2-3ca49f13332a", PasskeyProvider.SECURITY_KEY),
                Arguments.of("a25342c0-3cdc-4414-8e46-f4807fca511c", PasskeyProvider.SECURITY_KEY),
                Arguments.of("6028b017-b1d4-4c02-b4b3-afcdafc96bb2", PasskeyProvider.WINDOWS_HELLO),
                Arguments.of("19083c3d-8383-4b18-bc03-8f1c9ab2fd1b", PasskeyProvider.SECURITY_KEY),
                Arguments.of("cb69481e-8ff7-4039-93ec-0a2729a154a8", PasskeyProvider.SECURITY_KEY),
                Arguments.of("24673149-6c86-42e7-98d9-433fb5b73296", PasskeyProvider.SECURITY_KEY),
                Arguments.of("ee882879-721c-4913-9775-3dfcce97072a", PasskeyProvider.SECURITY_KEY),
                Arguments.of("ff4dac45-ede8-4ec2-aced-cf66103f4335", PasskeyProvider.SECURITY_KEY),
                Arguments.of("ea9b8d66-4d01-1d21-3ce4-b6b48cb575d4", PasskeyProvider.GOOGLE_PASSWORD_MANAGER),
                Arguments.of("dd4ec289-e01d-41c9-bb89-70fa845d4bf2", PasskeyProvider.ICLOUD_KEYCHAIN),
                Arguments.of("bada5566-a7aa-401f-bd96-45619a55120d", PasskeyProvider.ONE_PASSWORD),
                Arguments.of("d548826e-79b4-db40-a3d8-11116f7e8349", PasskeyProvider.BITWARDEN),
                Arguments.of("fbfc3007-154e-4ecc-8c0b-6e020557d7bd", PasskeyProvider.ICLOUD_KEYCHAIN));
    }

    static Stream<Arguments> publishedAttestations() {
        return Stream.of(
                Arguments.of(
                        "yubikey-packed-firefox",
                        "https://github.com/duo-labs/py_webauthn/blob/master/tests/test_verify_registration_response_packed.py",
                        "6d44ba9b-f6ec-2e49-b930-0c8fe920cb73",
                        null),
                Arguments.of(
                        "yubikey-packed-okp",
                        "https://github.com/duo-labs/py_webauthn/blob/master/tests/test_verify_registration_response_packed.py",
                        "c5ef55ff-ad9a-4b9f-b580-adebafe026d0",
                        PasskeyProvider.SECURITY_KEY),
                Arguments.of(
                        "tpm-surface-pro-4",
                        "https://github.com/duo-labs/py_webauthn/blob/master/tests/test_verify_registration_response_tpm.py",
                        "08987058-cadc-4b81-b6e1-30de50dcbe96",
                        PasskeyProvider.WINDOWS_HELLO),
                Arguments.of(
                        "apple-passkey",
                        "https://github.com/duo-labs/py_webauthn/blob/master/tests/test_verify_registration_response_apple.py",
                        "f24a8e70-d0d3-f82c-2937-32523cc4de5a",
                        null));
    }

    @ParameterizedTest
    @MethodSource("everyKnownAuthenticator")
    void namesTheProviderOfEveryAuthenticatorOfTheMap(String aaguid, PasskeyProvider provider) {
        assertThat(PasskeyProviders.providerOf(attestationWithAaguid(aaguid))).isEqualTo(provider);
    }

    @Test
    void theMapHoldsExactlyTheAuthenticatorsListedAbove() {
        assertThat(PasskeyProviders.knownAaguidCount())
                .isEqualTo(everyKnownAuthenticator().count());
    }

    @ParameterizedTest(name = "{0} ({1})")
    @MethodSource("publishedAttestations")
    void readsTheAaguidOfAnAttestationObjectPublishedByAnotherLibrary(
            String vector, String source, String aaguid, PasskeyProvider provider) {
        Bytes attestation = publishedAttestation(vector);

        assertThat(PasskeyProviders.aaguidOf(attestation)).isEqualTo(UUID.fromString(aaguid));
        assertThat(PasskeyProviders.providerOf(attestation)).isEqualTo(provider);
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

    private static Bytes publishedAttestation(String vector) {
        try {
            String encoded = new String(new ClassPathResource("attestations/" + vector + ".b64u")
                            .getInputStream()
                            .readAllBytes())
                    .trim();
            return new Bytes(Base64.getUrlDecoder().decode(encoded));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
