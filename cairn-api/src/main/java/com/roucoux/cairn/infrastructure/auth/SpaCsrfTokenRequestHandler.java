package com.roucoux.cairn.infrastructure.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.function.Supplier;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.csrf.CsrfTokenRequestHandler;
import org.springframework.security.web.csrf.XorCsrfTokenRequestAttributeHandler;
import org.springframework.util.StringUtils;

/**
 * Accepts the CSRF token in both shapes this application receives it, because it serves two kinds
 * of client. Spring renders an XOR-masked token into its own sign-in and sign-out forms, and only
 * the masked form can be decoded back. The single-page application never sees that: it reads the
 * raw token from the XSRF-TOKEN cookie and echoes it in a header, which is what Angular's
 * HttpClient does and all it can do.
 *
 * <p>Masking alone therefore rejected every write the application made — sign-out, imports,
 * deletions — while leaving reads untouched, since only unsafe methods carry a token.
 */
class SpaCsrfTokenRequestHandler extends CsrfTokenRequestAttributeHandler {

    private final CsrfTokenRequestHandler masked = new XorCsrfTokenRequestAttributeHandler();

    /** Always mask on the way out: Spring's own forms are the only thing rendering a token. */
    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, Supplier<CsrfToken> csrfToken) {
        masked.handle(request, response, csrfToken);
    }

    @Override
    public String resolveCsrfTokenValue(HttpServletRequest request, CsrfToken csrfToken) {
        if (StringUtils.hasText(request.getHeader(csrfToken.getHeaderName()))) {
            return super.resolveCsrfTokenValue(request, csrfToken);
        }

        return masked.resolveCsrfTokenValue(request, csrfToken);
    }
}
