package com.roucoux.cairn.application.mapper;

import com.roucoux.cairn.generated.model.PasskeyResponse;
import com.roucoux.cairn.generated.model.SessionResponse;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import org.springframework.security.web.webauthn.api.CredentialRecord;
import org.springframework.stereotype.Component;

@Component
public class SessionRestMapper {

    public SessionResponse toResponse(String displayName, List<CredentialRecord> passkeys) {
        SessionResponse response = new SessionResponse();
        response.setDisplayName(displayName);
        response.setInitials(initialsOf(displayName));
        response.setPasskeys(passkeys.stream().map(this::toResponse).toList());
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

    private PasskeyResponse toResponse(CredentialRecord credential) {
        PasskeyResponse response = new PasskeyResponse();
        response.setCredentialId(credential.getCredentialId().toBase64UrlString());
        response.setLabel(credential.getLabel());
        response.setCreatedAt(credential.getCreated().atOffset(ZoneOffset.UTC));
        response.setLastUsedAt(
                credential.getLastUsed() == null
                        ? null
                        : credential.getLastUsed().atOffset(ZoneOffset.UTC));
        return response;
    }
}
