package com.roucoux.cairn.infrastructure.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Stateless OAuth2 resource server: every request needs a valid JWT, except the health probe. CSRF
 * stays enabled — the credential travels in a session cookie (see the WebAuthn work), so a write
 * without a CSRF token must be refused. The {@code local} profile sets
 * {@code app.security.permit-all=true} to develop without an IdP.
 */
@Configuration(proxyBeanMethods = false)
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http, @Value("${app.security.permit-all:false}") boolean permitAll) throws Exception {
        http.sessionManagement(sessions -> sessions.sessionCreationPolicy(SessionCreationPolicy.STATELESS));
        if (permitAll) {
            return http.authorizeHttpRequests(requests -> requests.anyRequest().permitAll())
                    .build();
        }
        return http.authorizeHttpRequests(requests -> requests.requestMatchers("/actuator/health/**")
                        .permitAll()
                        .anyRequest()
                        .authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()))
                .build();
    }
}
