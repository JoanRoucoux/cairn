package com.roucoux.cairn.application.mapper;

import com.roucoux.cairn.generated.model.PasskeyResponse;
import com.roucoux.cairn.generated.model.SessionResponse;
import com.roucoux.cairn.infrastructure.auth.PasskeyAuthentication;
import com.roucoux.cairn.infrastructure.auth.PasskeyProviders;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.webauthn.api.CredentialRecord;
import org.springframework.security.web.webauthn.authentication.WebAuthnAuthentication;
import org.springframework.stereotype.Component;

@Component
public class SessionRestMapper {

    public SessionResponse toResponse(
            Authentication authentication, String displayName, List<CredentialRecord> passkeys) {
        String currentCredentialId =
                authentication instanceof PasskeyAuthentication passkey ? passkey.credentialId() : null;
        SessionResponse response = new SessionResponse();
        response.setDisplayName(displayName);
        response.setInitials(initialsOf(displayName));
        response.setUsername(authentication.getName());
        response.setSignInMethod(
                authentication instanceof WebAuthnAuthentication
                        ? SessionResponse.SignInMethodEnum.PASSKEY
                        : SessionResponse.SignInMethodEnum.PASSWORD);
        response.setPasskeys(passkeys.stream()
                .map(passkey -> toResponse(passkey, currentCredentialId))
                .toList());
        return response;
    }

    public String initialsOf(String displayName) {
        String[] words = displayName.trim().split("\\s+");
        if (words[0].isEmpty()) {
            return "";
        }
        if (words.length == 1) {
            return words[0].substring(0, Math.min(2, words[0].length())).toUpperCase(Locale.ROOT);
        }
        return ("" + words[0].charAt(0) + words[1].charAt(0)).toUpperCase(Locale.ROOT);
    }

    private PasskeyResponse toResponse(CredentialRecord credential, String currentCredentialId) {
        PasskeyResponse response = new PasskeyResponse();
        String credentialId = credential.getCredentialId().toBase64UrlString();
        response.setCredentialId(credentialId);
        response.setCurrent(credentialId.equals(currentCredentialId));
        response.setProvider(PasskeyProviders.providerOf(credential.getAttestationObject()));
        response.setLabel(credential.getLabel());
        response.setCreatedAt(credential.getCreated().atOffset(ZoneOffset.UTC));
        response.setLastUsedAt(
                credential.getLastUsed() == null
                        ? null
                        : credential.getLastUsed().atOffset(ZoneOffset.UTC));
        return response;
    }
}
