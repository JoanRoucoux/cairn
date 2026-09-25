package com.roucoux.cairn.application.controller;

import com.roucoux.cairn.application.exception.LastPasskeyException;
import com.roucoux.cairn.application.mapper.SessionRestMapper;
import com.roucoux.cairn.domain.exception.business.NotFoundException;
import com.roucoux.cairn.generated.api.SessionApi;
import com.roucoux.cairn.generated.model.SessionResponse;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.webauthn.api.CredentialRecord;
import org.springframework.security.web.webauthn.api.PublicKeyCredentialUserEntity;
import org.springframework.security.web.webauthn.management.PublicKeyCredentialUserEntityRepository;
import org.springframework.security.web.webauthn.management.UserCredentialRepository;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.web.bind.annotation.RestController;

@RestController
class SessionController implements SessionApi {

    private final PublicKeyCredentialUserEntityRepository userEntities;
    private final UserCredentialRepository credentials;
    private final SessionRestMapper mapper;
    private final FindByIndexNameSessionRepository<? extends Session> sessions;
    private final HttpServletRequest request;

    SessionController(
            PublicKeyCredentialUserEntityRepository userEntities,
            UserCredentialRepository credentials,
            SessionRestMapper mapper,
            FindByIndexNameSessionRepository<? extends Session> sessions,
            HttpServletRequest request) {
        this.userEntities = userEntities;
        this.credentials = credentials;
        this.mapper = mapper;
        this.sessions = sessions;
        this.request = request;
    }

    @Override
    public ResponseEntity<SessionResponse> getSession() {
        String username = signedInUsername();
        PublicKeyCredentialUserEntity owner = userEntities.findByUsername(username);
        String displayName = owner == null ? username : owner.getDisplayName();
        return ResponseEntity.ok(mapper.toResponse(displayName, passkeysOf(owner)));
    }

    @Override
    public ResponseEntity<Void> revokePasskey(String credentialId) {
        String username = signedInUsername();
        List<CredentialRecord> owned = passkeysOf(userEntities.findByUsername(username));

        CredentialRecord target = owned.stream()
                .filter(credential ->
                        credential.getCredentialId().toBase64UrlString().equals(credentialId))
                .findFirst()
                .orElseThrow(() -> new NotFoundException("passkey", credentialId));

        if (owned.size() == 1) {
            throw new LastPasskeyException();
        }

        signOutEveryOtherSessionOf(username);
        credentials.delete(target.getCredentialId());
        return ResponseEntity.noContent().build();
    }

    private String signedInUsername() {
        return SecurityContextHolder.getContext().getAuthentication().getName();
    }

    private void signOutEveryOtherSessionOf(String username) {
        String currentSessionId = request.getSession().getId();
        sessions.findByPrincipalName(username).keySet().stream()
                .filter(sessionId -> !sessionId.equals(currentSessionId))
                .forEach(sessions::deleteById);
    }

    private List<CredentialRecord> passkeysOf(PublicKeyCredentialUserEntity owner) {
        return owner == null ? List.of() : credentials.findByUserId(owner.getId());
    }
}
