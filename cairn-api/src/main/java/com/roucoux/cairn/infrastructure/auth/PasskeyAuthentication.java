package com.roucoux.cairn.infrastructure.auth;

import java.util.Collection;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.web.webauthn.api.PublicKeyCredentialUserEntity;
import org.springframework.security.web.webauthn.authentication.WebAuthnAuthentication;

public class PasskeyAuthentication extends WebAuthnAuthentication {

    private static final long serialVersionUID = 1L;

    private final String credentialId;

    public PasskeyAuthentication(
            PublicKeyCredentialUserEntity principal,
            Collection<? extends GrantedAuthority> authorities,
            String credentialId) {
        super(principal, authorities);
        this.credentialId = credentialId;
    }

    public String credentialId() {
        return credentialId;
    }
}
