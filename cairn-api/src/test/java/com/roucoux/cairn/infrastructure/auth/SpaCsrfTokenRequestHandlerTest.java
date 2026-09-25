package com.roucoux.cairn.infrastructure.auth;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.DefaultCsrfToken;
import org.springframework.security.web.csrf.XorCsrfTokenRequestAttributeHandler;

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

    private String maskedToken() {
        MockHttpServletRequest rendering = new MockHttpServletRequest();
        new XorCsrfTokenRequestAttributeHandler().handle(rendering, new MockHttpServletResponse(), () -> token);
        return ((CsrfToken) rendering.getAttribute(CsrfToken.class.getName())).getToken();
    }
}
