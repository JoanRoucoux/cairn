package com.roucoux.cairn.infrastructure.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

/**
 * No Spring context: parses the two YAML files directly, the same way WebAuthnConfigTest calls
 * bean-wiring methods directly. A regression here would silently reintroduce HTTPS-only cookies
 * under the local profile, whose whole point is running over plain HTTP.
 */
class SessionCookiePropertiesTest {

    private final YamlPropertySourceLoader loader = new YamlPropertySourceLoader();

    @Test
    void theBaseConfigRequiresASecureCookie() throws Exception {
        assertThat(cookieSecureIn("application.yml")).isEqualTo(true);
    }

    @Test
    void theLocalProfileRelaxesItForPlainHttp() throws Exception {
        assertThat(cookieSecureIn("application-local.yml")).isEqualTo(false);
    }

    private Object cookieSecureIn(String fileName) throws Exception {
        List<PropertySource<?>> sources = loader.load(fileName, new ClassPathResource(fileName));
        return sources.get(0).getProperty("server.servlet.session.cookie.secure");
    }
}
