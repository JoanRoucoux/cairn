package com.roucoux.cairn.infrastructure.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.CsrfConfigurer;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.XorCsrfTokenRequestAttributeHandler;
import org.springframework.security.web.webauthn.management.JdbcPublicKeyCredentialUserEntityRepository;
import org.springframework.security.web.webauthn.management.JdbcUserCredentialRepository;
import org.springframework.security.web.webauthn.management.PublicKeyCredentialUserEntityRepository;
import org.springframework.security.web.webauthn.management.UserCredentialRepository;

/**
 * Session-based WebAuthn (passkey) authentication: every request needs an authenticated session,
 * except the health probe. CSRF stays enabled, the credential travels in a session cookie. The
 * {@code local} profile sets {@code app.security.permit-all=true} to develop without registering a
 * passkey — WebAuthn requires a real {@code rpId} and an https origin.
 *
 * <p>Cairn is a single-user application: registering the first passkey needs an already
 * authenticated session to attach the credential to, so a single in-memory account backs the
 * form-login fallback that bootstraps that first registration.
 *
 * <p>The client is a single-page app talking to a JSON API, never a browser following a
 * server-driven redirect: an unauthenticated request gets a plain 401, not a redirect to
 * {@code /login} (Spring Security's form-login default).
 */
@Configuration(proxyBeanMethods = false)
@EnableWebSecurity
public class WebAuthnConfig {

    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            @Value("${app.webauthn.rp-id}") String rpId,
            @Value("${app.webauthn.allowed-origins}") String allowedOrigins,
            @Value("${app.security.permit-all:false}") boolean permitAll)
            throws Exception {
        if (permitAll) {
            return http.csrf(CsrfConfigurer::disable)
                    .authorizeHttpRequests(requests -> requests.anyRequest().permitAll())
                    .build();
        }
        return http.webAuthn(webAuthn -> webAuthn.rpName("Cairn").rpId(rpId).allowedOrigins(allowedOrigins))
                .formLogin(Customizer.withDefaults())
                .csrf(csrf -> csrf.csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                        .csrfTokenRequestHandler(new XorCsrfTokenRequestAttributeHandler()))
                // defaultAuthenticationEntryPointFor, not authenticationEntryPoint: the latter
                // disables Spring Security's own detection of whether to serve the default /login
                // page, so GET /login would 404 instead of rendering the passkey registration page.
                .exceptionHandling(exceptions -> exceptions.defaultAuthenticationEntryPointFor(
                        new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED), request -> true))
                .authorizeHttpRequests(requests -> requests.requestMatchers("/actuator/health/**")
                        .permitAll()
                        .anyRequest()
                        .authenticated())
                .build();
    }

    @Bean
    PublicKeyCredentialUserEntityRepository userEntityRepository(JdbcOperations jdbc) {
        return new JdbcPublicKeyCredentialUserEntityRepository(jdbc);
    }

    @Bean
    UserCredentialRepository userCredentialRepository(JdbcOperations jdbc) {
        return new JdbcUserCredentialRepository(jdbc);
    }

    @Bean
    UserDetailsService userDetailsService(
            PasswordEncoder passwordEncoder,
            @Value("${app.security.username:joan}") String username,
            @Value("${app.security.password:changeme}") String password) {
        return new InMemoryUserDetailsManager(User.withUsername(username)
                .password(passwordEncoder.encode(password))
                .roles("USER")
                .build());
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }
}
