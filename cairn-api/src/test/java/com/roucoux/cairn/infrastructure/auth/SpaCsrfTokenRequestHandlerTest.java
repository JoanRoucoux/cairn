package com.roucoux.cairn.infrastructure.auth;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.DefaultCsrfToken;
import org.springframework.security.web.csrf.XorCsrfTokenRequestAttributeHandler;

/**
 * The two clients of this application send the CSRF token in two different shapes, and the server
 * has to accept both. Spring renders an XOR-masked token into its own sign-in form; the single-page
 * application reads the raw token from the XSRF-TOKEN cookie and echoes it in a header, because
 * that is what Angular's HttpClient does and it cannot mask what it never received unmasked.
 */
class SpaCsrfTokenRequestHandlerTest {

    private static final String HEADER = "X-XSRF-TOKEN";
    private static final String PARAMETER = "_csrf";
    private static final String RAW_TOKEN = "a4f1c8de-0000-4000-8000-000000000000";

    private final SpaCsrfTokenRequestHandler handler = new SpaCsrfTokenRequestHandler();
    private final CsrfToken token = new DefaultCsrfToken(HEADER, PARAMETER, RAW_TOKEN);

    @Test
    void acceptsTheRawTokenTheSinglePageApplicationEchoesFromItsCookie() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(HEADER, RAW_TOKEN);

        assertThat(handler.resolveCsrfTokenValue(request, token)).isEqualTo(RAW_TOKEN);
    }

    @Test
    void stillAcceptsTheMaskedTokenSpringRendersIntoItsOwnForm() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setParameter(PARAMETER, maskedToken());

        assertThat(handler.resolveCsrfTokenValue(request, token)).isEqualTo(RAW_TOKEN);
    }

    /** The value Spring writes into the hidden {@code _csrf} field, obtained the way it produces it. */
    private String maskedToken() {
        MockHttpServletRequest rendering = new MockHttpServletRequest();
        new XorCsrfTokenRequestAttributeHandler().handle(rendering, new MockHttpServletResponse(), () -> token);
        return ((CsrfToken) rendering.getAttribute(CsrfToken.class.getName())).getToken();
    }
}
