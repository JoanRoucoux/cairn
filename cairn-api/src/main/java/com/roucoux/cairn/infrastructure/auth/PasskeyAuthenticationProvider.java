package com.roucoux.cairn.infrastructure.auth;

import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.webauthn.authentication.WebAuthnAuthentication;
import org.springframework.security.web.webauthn.authentication.WebAuthnAuthenticationProvider;
import org.springframework.security.web.webauthn.authentication.WebAuthnAuthenticationRequestToken;

public class PasskeyAuthenticationProvider implements AuthenticationProvider {

    private final WebAuthnAuthenticationProvider delegate;

    public PasskeyAuthenticationProvider(WebAuthnAuthenticationProvider delegate) {
        this.delegate = delegate;
    }

    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        WebAuthnAuthentication verified = (WebAuthnAuthentication) delegate.authenticate(authentication);
        String credentialId = ((WebAuthnAuthenticationRequestToken) authentication)
                .getWebAuthnRequest()
                .getPublicKey()
                .getRawId()
                .toBase64UrlString();
        return new PasskeyAuthentication(verified.getPrincipal(), verified.getAuthorities(), credentialId);
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return delegate.supports(authentication);
    }
}
