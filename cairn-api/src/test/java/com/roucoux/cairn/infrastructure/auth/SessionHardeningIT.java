package com.roucoux.cairn.infrastructure.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.Filter;
import jakarta.servlet.http.Cookie;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.authentication.session.ChangeSessionIdAuthenticationStrategy;
import org.springframework.security.web.authentication.session.CompositeSessionAuthenticationStrategy;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.csrf.CsrfAuthenticationStrategy;
import org.springframework.security.web.webauthn.authentication.WebAuthnAuthenticationFilter;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@TestPropertySource(
        properties = {
            "spring.liquibase.change-log=classpath:db/changelog/changelog-master.xml",
            "app.security.password=a-real-password",
            "app.security.username=alex"
        })
@Testcontainers
class SessionHardeningIT {

    private static final String COOKIE = "__Host-SESSION";

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private FilterChainProxy filterChainProxy;

    @Test
    void thePasskeyFilterSharesTheFormLoginSessionStrategyCsrfIncluded() {
        List<Filter> filters = filterChainProxy.getFilterChains().stream()
                .map(SecurityFilterChain::getFilters)
                .flatMap(List::stream)
                .toList();
        SessionAuthenticationStrategy passkey = strategyOf(filters, WebAuthnAuthenticationFilter.class);
        SessionAuthenticationStrategy password = strategyOf(filters, UsernamePasswordAuthenticationFilter.class);

        assertThat(passkey).isSameAs(password).isInstanceOf(CompositeSessionAuthenticationStrategy.class);
        @SuppressWarnings("unchecked")
        List<SessionAuthenticationStrategy> delegates =
                (List<SessionAuthenticationStrategy>) ReflectionTestUtils.getField(passkey, "delegateStrategies");
        assertThat(delegates)
                .hasAtLeastOneElementOfType(ChangeSessionIdAuthenticationStrategy.class)
                .hasAtLeastOneElementOfType(CsrfAuthenticationStrategy.class)
                .hasAtLeastOneElementOfType(SignedInSessionLifetime.class);
    }

    @Test
    void theSessionBeforeSignInLivesFifteenMinutesAndThirtyDaysAfter() throws Exception {
        MvcResult options = mockMvc.perform(
                        post("/webauthn/authenticate/options").with(csrf()))
                .andExpect(status().isOk())
                .andReturn();
        Cookie preAuthentication = options.getResponse().getCookie(COOKIE);
        assertThat(preAuthentication).isNotNull();
        assertThat(maxInactiveInterval(preAuthentication)).isEqualTo(900);

        MvcResult signIn = mockMvc.perform(post("/authenticate")
                        .cookie(preAuthentication)
                        .param("username", "alex")
                        .param("password", "a-real-password")
                        .with(csrf()))
                .andExpect(status().isNoContent())
                .andReturn();

        Cookie signedIn = signIn.getResponse().getCookie(COOKIE);
        assertThat(signedIn).isNotNull();
        assertThat(maxInactiveInterval(signedIn)).isEqualTo(2_592_000);
        assertThat(signedIn.getValue()).isNotEqualTo(preAuthentication.getValue());
    }

    private Integer maxInactiveInterval(Cookie session) {
        String sessionId = new String(Base64.getDecoder().decode(session.getValue()), StandardCharsets.UTF_8);
        return jdbc.queryForObject(
                "select max_inactive_interval from spring_session where session_id = ?", Integer.class, sessionId);
    }

    private SessionAuthenticationStrategy strategyOf(List<Filter> filters, Class<? extends Filter> type) {
        Filter filter = filters.stream().filter(type::isInstance).findFirst().orElseThrow();
        return (SessionAuthenticationStrategy) ReflectionTestUtils.getField(filter, "sessionStrategy");
    }
}
