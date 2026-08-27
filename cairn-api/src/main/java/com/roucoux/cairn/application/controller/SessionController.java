package com.roucoux.cairn.application.controller;

import com.roucoux.cairn.application.exception.LastPasskeyException;
import com.roucoux.cairn.application.mapper.SessionRestMapper;
import com.roucoux.cairn.domain.exception.business.NotFoundException;
import com.roucoux.cairn.generated.api.SessionApi;
import com.roucoux.cairn.generated.model.SessionResponse;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.webauthn.api.CredentialRecord;
import org.springframework.security.web.webauthn.api.PublicKeyCredentialUserEntity;
import org.springframework.security.web.webauthn.management.PublicKeyCredentialUserEntityRepository;
import org.springframework.security.web.webauthn.management.UserCredentialRepository;
import org.springframework.web.bind.annotation.RestController;

/**
 * Talks directly to Spring Security's WebAuthn repositories instead of a domain port:
 * authentication is not portfolio data, so ArchUnit's domain-access rules do not apply to this
 * slice.
 */
@RestController
class SessionController implements SessionApi {

    private final PublicKeyCredentialUserEntityRepository userEntities;
    private final UserCredentialRepository credentials;
    private final SessionRestMapper mapper;

    SessionController(
            PublicKeyCredentialUserEntityRepository userEntities,
            UserCredentialRepository credentials,
            SessionRestMapper mapper) {
        this.userEntities = userEntities;
        this.credentials = credentials;
        this.mapper = mapper;
    }

    @Override
    public ResponseEntity<SessionResponse> getSession() {
        PublicKeyCredentialUserEntity owner = signedInOwner();
        return ResponseEntity.ok(mapper.toResponse(owner.getDisplayName(), passkeysOf(owner)));
    }

    @Override
    public ResponseEntity<Void> revokePasskey(String credentialId) {
        List<CredentialRecord> owned = passkeysOf(signedInOwner());

        CredentialRecord target = owned.stream()
                .filter(credential ->
                        credential.getCredentialId().toBase64UrlString().equals(credentialId))
                .findFirst()
                .orElseThrow(() -> new NotFoundException("passkey", credentialId));

        if (owned.size() == 1) {
            throw new LastPasskeyException();
        }

        credentials.delete(target.getCredentialId());
        return ResponseEntity.noContent().build();
    }

    private PublicKeyCredentialUserEntity signedInOwner() {
        // The generated interface fixes the method signature, so the authentication cannot arrive
        // as a parameter: it is read from the context the security filter chain already populated.
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        return userEntities.findByUsername(username);
    }

    private List<CredentialRecord> passkeysOf(PublicKeyCredentialUserEntity owner) {
        return credentials.findByUserId(owner.getId());
    }
}
