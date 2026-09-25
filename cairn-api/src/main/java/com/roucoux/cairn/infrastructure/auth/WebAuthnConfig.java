package com.roucoux.cairn.infrastructure.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcOperations;
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
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.webauthn.management.JdbcPublicKeyCredentialUserEntityRepository;
import org.springframework.security.web.webauthn.management.JdbcUserCredentialRepository;
import org.springframework.security.web.webauthn.management.PublicKeyCredentialUserEntityRepository;
import org.springframework.security.web.webauthn.management.UserCredentialRepository;

@Configuration(proxyBeanMethods = false)
@EnableWebSecurity
public class WebAuthnConfig {

    static final String DEFAULT_PASSWORD = "changeme";

    static final String LOCAL_PROFILE = "local";

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
                .csrf(csrf -> csrf.csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                        .csrfTokenRequestHandler(new SpaCsrfTokenRequestHandler()))
                // Not authenticationEntryPoint: that one makes GET /webauthn/register answer 404.
                .exceptionHandling(exceptions -> exceptions.defaultAuthenticationEntryPointFor(
                        new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED), request -> true))
                .authorizeHttpRequests(requests -> requests.requestMatchers("/actuator/health/**")
                        .permitAll()
                        .anyRequest()
                        .authenticated())
                // A login page this application does not serve switches off Spring's generated one.
                .formLogin(form -> form.loginPage("/login")
                        .loginProcessingUrl("/authenticate")
                        .successHandler((request, response, authentication) ->
                                response.setStatus(HttpStatus.NO_CONTENT.value()))
                        .failureHandler(
                                (request, response, exception) -> response.setStatus(HttpStatus.UNAUTHORIZED.value())))
                .logout(logout -> logout.logoutSuccessHandler(
                        (request, response, authentication) -> response.setStatus(HttpStatus.NO_CONTENT.value())))
                .addFilterAfter(new CsrfCookieFilter(), BasicAuthenticationFilter.class)
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
            Environment environment,
            @Value("${app.security.username:joan}") String username,
            @Value("${app.security.password:" + DEFAULT_PASSWORD + "}") String password) {
        if (DEFAULT_PASSWORD.equals(password) && !environment.matchesProfiles(LOCAL_PROFILE)) {
            throw new IllegalStateException(
                    "app.security.password (CAIRN_PASSWORD) must be set outside the local profile");
        }
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
