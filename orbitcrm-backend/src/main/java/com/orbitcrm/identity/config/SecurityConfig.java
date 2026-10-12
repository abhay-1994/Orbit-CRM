package com.orbitcrm.identity.config;

import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.header.writers.DelegatingRequestMatcherHeaderWriter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.security.web.header.writers.StaticHeadersWriter;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;

import tools.jackson.databind.json.JsonMapper;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import com.orbitcrm.identity.security.JsonSecurityHandlers;
import com.orbitcrm.identity.security.JwtService;
import com.orbitcrm.identity.security.OrbitJwtAuthenticationConverter;
import com.orbitcrm.identity.security.RateLimitFilter;
import com.orbitcrm.identity.security.TenantContextFilter;
import com.orbitcrm.shared.config.OrbitProperties;

/**
 * Spring Security configuration of the API: stateless JWT bearer authentication, CORS, security headers and the
 * custom filters.
 *
 * <p>Purpose: define who may call which endpoint and how requests are authenticated.
 * <ul>
 *   <li>Only the public auth endpoints ({@link #PUBLIC_AUTH_ENDPOINTS}: register, slug availability, login,
 *   MFA login, refresh, logout, password forgot/reset, invitation accept), health/info/prometheus, the OpenAPI
 *   documentation and {@code /error} are anonymous; everything else requires a valid access token.</li>
 *   <li>Access tokens are validated by {@code JwtService}'s HS256 decoder and turned into a
 *   {@code CurrentUser} principal by {@code OrbitJwtAuthenticationConverter}.</li>
 *   <li>No HTTP session, form login, basic auth or CSRF token: API requests carry the token in the
 *   {@code Authorization} header, which browsers never attach automatically. The only cookie-based endpoints
 *   (refresh and logout) use a SameSite=Strict cookie plus a mandatory custom header.</li>
 *   <li>After authentication, {@code TenantContextFilter} binds the tenant and {@code RateLimitFilter} applies
 *   per-user / per-IP limits.</li>
 *   <li>Response headers: frames denied, no referrer, and a {@code default-src 'none'} Content-Security-Policy
 *   for {@code /api/**}.</li>
 *   <li>CORS for {@code /api/**} allows only the configured frontend origins, with credentials, and exposes
 *   {@code X-Request-Id}, {@code ETag}, {@code Location} and {@code Content-Disposition}.</li>
 *   <li>Passwords are hashed with Spring's delegating encoder (bcrypt by default, upgradeable).</li>
 * </ul>
 *
 * @author Abhay
 */
@Configuration(proxyBeanMethods = false)
@EnableWebSecurity
public class SecurityConfig {

    public static final String[] PUBLIC_AUTH_ENDPOINTS = {
            "/api/v1/auth/register",
            "/api/v1/auth/slug-availability",
            "/api/v1/auth/login",
            "/api/v1/auth/login/mfa",
            "/api/v1/auth/refresh",
            "/api/v1/auth/logout",
            "/api/v1/auth/password/forgot",
            "/api/v1/auth/password/reset",
            "/api/v1/auth/invitations/accept",
    };

    @Bean
    SecurityFilterChain apiSecurity(HttpSecurity http, JwtService jwtService,
                                    OrbitJwtAuthenticationConverter converter,
                                    JsonSecurityHandlers handlers, OrbitProperties properties,
                                    JsonMapper jsonMapper) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .cors(cors -> { })
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .httpBasic(basic -> basic.disable())
                .formLogin(form -> form.disable())
                .logout(logout -> logout.disable())
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers(PUBLIC_AUTH_ENDPOINTS).permitAll()
                        .requestMatchers("/actuator/health/**", "/actuator/info", "/actuator/prometheus").permitAll()
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        .requestMatchers("/error").permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth -> oauth
                        .jwt(jwt -> jwt.decoder(jwtService.accessTokenDecoder()).jwtAuthenticationConverter(converter))
                        .authenticationEntryPoint(handlers.entryPoint())
                        .accessDeniedHandler(handlers.accessDeniedHandler()))
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(handlers.entryPoint())
                        .accessDeniedHandler(handlers.accessDeniedHandler()))
                .headers(headers -> headers
                        .frameOptions(frame -> frame.deny())
                        .referrerPolicy(ref -> ref.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER))
                        // JSON APIs never need to load anything; Swagger UI (outside /api) keeps its defaults.
                        .addHeaderWriter(new DelegatingRequestMatcherHeaderWriter(
                                PathPatternRequestMatcher.withDefaults().matcher("/api/**"),
                                new StaticHeadersWriter("Content-Security-Policy",
                                        "default-src 'none'; frame-ancestors 'none'"))))
                .addFilterAfter(new TenantContextFilter(), BearerTokenAuthenticationFilter.class)
                .addFilterAfter(new RateLimitFilter(properties.rateLimit(), jsonMapper), TenantContextFilter.class);
        return http.build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource(OrbitProperties properties) {
        CorsConfiguration config = new CorsConfiguration();
        List<String> origins = properties.web().corsAllowedOrigins();
        config.setAllowedOrigins(origins == null ? List.of() : origins);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-Requested-With", "X-Request-Id",
                "If-Match"));
        config.setExposedHeaders(List.of("X-Request-Id", "ETag", "Location", "Content-Disposition"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }
}
