package com.roucoux.cairn.application.mapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.roucoux.cairn.generated.model.PasskeyResponse;
import com.roucoux.cairn.generated.model.SessionResponse;
import com.roucoux.cairn.infrastructure.auth.AttestationFixtures;
import com.roucoux.cairn.infrastructure.auth.PasskeyAuthentication;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.webauthn.api.Bytes;
import org.springframework.security.web.webauthn.api.CredentialRecord;
import org.springframework.security.web.webauthn.api.ImmutableCredentialRecord;
import org.springframework.security.web.webauthn.api.ImmutablePublicKeyCose;
import org.springframework.security.web.webauthn.api.ImmutablePublicKeyCredentialUserEntity;
import org.springframework.security.web.webauthn.api.PublicKeyCredentialUserEntity;
import org.springframework.security.web.webauthn.authentication.WebAuthnAuthentication;

class SessionRestMapperTest {

    private final SessionRestMapper mapper = new SessionRestMapper();

    private static final Bytes ICLOUD_KEY = Bytes.fromBase64("aXBob25l");
    private static final Bytes MAC_KEY = Bytes.fromBase64("bWFj");

    private static PublicKeyCredentialUserEntity owner() {
        return ImmutablePublicKeyCredentialUserEntity.builder()
                .name("alex")
                .id(Bytes.random())
                .displayName("Alex Martin")
                .build();
    }

    private static CredentialRecord credential(Bytes id, Bytes attestation) {
        return credential(id, attestation, Instant.parse("2026-01-01T00:00:00Z"));
    }

    private static CredentialRecord credential(Bytes id, Bytes attestation, Instant created) {
        return ImmutableCredentialRecord.builder()
                .credentialId(id)
                .userEntityUserId(Bytes.random())
                .publicKey(new ImmutablePublicKeyCose(new byte[] {1}))
                .label("key")
                .created(created)
                .attestationObject(attestation)
                .build();
    }

    @Test
    void takesOneInitialFromEachOfTheFirstTwoWords() {
        assertThat(mapper.initialsOf("Alex Martin")).isEqualTo("AM");
    }

    @Test
    void ignoresTheWordsAfterTheSecond() {
        assertThat(mapper.initialsOf("Jean Pierre Marie Dupont")).isEqualTo("JP");
    }

    @Test
    void takesTwoLettersFromASingleWord() {
        assertThat(mapper.initialsOf("alex")).isEqualTo("AL");
    }

    @Test
    void takesTheOnlyLetterOfAOneLetterName() {
        assertThat(mapper.initialsOf("J")).isEqualTo("J");
    }

    @Test
    void survivesAnEmptyDisplayName() {
        assertThat(mapper.initialsOf("  ")).isEmpty();
    }

    @Test
    void ordersThePasskeysOldestFirstThenByCredentialId() {
        Authentication password = UsernamePasswordAuthenticationToken.authenticated("alex", "n/a", List.of());
        Instant earlier = Instant.parse("2026-01-01T00:00:00Z");
        Instant later = Instant.parse("2026-02-01T00:00:00Z");

        List<PasskeyResponse> response = mapper.toPasskeys(
                password,
                List.of(
                        credential(MAC_KEY, null, later),
                        credential(ICLOUD_KEY, null, null),
                        credential(MAC_KEY, null, earlier),
                        credential(ICLOUD_KEY, null, later)));

        assertThat(response)
                .extracting(
                        PasskeyResponse::getCredentialId,
                        p -> p.getCreatedAt() == null ? null : p.getCreatedAt().toInstant())
                .containsExactly(
                        tuple("bWFj", earlier),
                        tuple("aXBob25l", later),
                        tuple("bWFj", later),
                        tuple("aXBob25l", null));
    }

    @Test
    void exposesTheUsernameAndFlagsAPasskeySignIn() {
        Authentication passkey = new PasskeyAuthentication(owner(), List.of(), ICLOUD_KEY.toBase64UrlString());

        SessionResponse response = mapper.toResponse(passkey, "Alex Martin");

        assertThat(response.getUsername()).isEqualTo("alex");
        assertThat(response.getSignInMethod()).isEqualTo(SessionResponse.SignInMethodEnum.PASSKEY);
    }

    @Test
    void flagsAPasswordSignIn() {
        Authentication password = UsernamePasswordAuthenticationToken.authenticated("alex", "n/a", List.of());

        SessionResponse response = mapper.toResponse(password, "Alex Martin");

        assertThat(response.getSignInMethod()).isEqualTo(SessionResponse.SignInMethodEnum.PASSWORD);
    }

    @Test
    void marksOnlyThePasskeyThatOpenedTheSessionAsCurrent() {
        Authentication passkey = new PasskeyAuthentication(owner(), List.of(), ICLOUD_KEY.toBase64UrlString());

        List<PasskeyResponse> response =
                mapper.toPasskeys(passkey, List.of(credential(ICLOUD_KEY, null), credential(MAC_KEY, null)));

        assertThat(response).extracting(PasskeyResponse::getCurrent).containsExactly(true, false);
    }

    @Test
    void marksNoPasskeyAsCurrentWhenThePasswordOpenedTheSession() {
        Authentication password = UsernamePasswordAuthenticationToken.authenticated("alex", "n/a", List.of());

        List<PasskeyResponse> response = mapper.toPasskeys(password, List.of(credential(ICLOUD_KEY, null)));

        assertThat(response).extracting(PasskeyResponse::getCurrent).containsExactly(false);
    }

    @Test
    void marksNoPasskeyAsCurrentForASessionOpenedBeforeTheCredentialWasRemembered() {
        Authentication legacy = new WebAuthnAuthentication(owner(), List.of());

        List<PasskeyResponse> response = mapper.toPasskeys(legacy, List.of(credential(ICLOUD_KEY, null)));

        assertThat(mapper.toResponse(legacy, "Alex Martin").getSignInMethod())
                .isEqualTo(SessionResponse.SignInMethodEnum.PASSKEY);
        assertThat(response).extracting(PasskeyResponse::getCurrent).containsExactly(false);
    }

    @Test
    void namesTheProviderFromTheAttestationAndLeavesItNullWhenUnknown() {
        Authentication password = UsernamePasswordAuthenticationToken.authenticated("alex", "n/a", List.of());

        List<PasskeyResponse> response = mapper.toPasskeys(
                password,
                List.of(
                        credential(ICLOUD_KEY, AttestationFixtures.attestationWithAaguid(AttestationFixtures.ICLOUD)),
                        credential(MAC_KEY, AttestationFixtures.attestationWithAaguid(AttestationFixtures.NONE))));

        assertThat(response)
                .extracting(PasskeyResponse::getProvider)
                .containsExactly(PasskeyResponse.ProviderEnum.ICLOUD_KEYCHAIN, null);
    }
}
