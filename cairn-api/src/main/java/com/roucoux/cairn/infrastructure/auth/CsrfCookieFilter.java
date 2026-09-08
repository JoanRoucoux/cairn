package com.roucoux.cairn.infrastructure.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Forces the CSRF token into its cookie on every request.
 *
 * <p>The token is deferred: CookieCsrfTokenRepository writes nothing until something reads the
 * value. Spring's own sign-in page used to be that reader, and this application no longer serves
 * one, so a browser that only ever calls the API would hold no token and every write it attempted
 * would be refused.
 */
final class CsrfCookieFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        CsrfToken token = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
        if (token != null) {
            token.getToken();
        }

        chain.doFilter(request, response);
    }
}
