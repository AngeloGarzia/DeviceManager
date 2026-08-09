package com.devicemanager.config;

import com.devicemanager.security.JwtAuthenticationFilter;
import com.devicemanager.security.LoginRateLimitFilter;
import com.devicemanager.security.Roles;
import com.devicemanager.tenancy.AtelierContextFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * Configuration Spring Security : API stateless JWT, CORS et règles d'accès par rôle.
 * <p>
 * Chaîne de filtres : {@link LoginRateLimitFilter} → {@link JwtAuthenticationFilter} →
 * {@link AtelierContextFilter}. CSRF désactivé (API REST). Sessions désactivées
 * ({@link SessionCreationPolicy#STATELESS}).
 */
@Configuration
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final AtelierContextFilter atelierContextFilter;
    private final LoginRateLimitFilter loginRateLimitFilter;
    private final Environment environment;

    @Value("${app.cors.allowed-origins:}")
    private String allowedOrigins;

    @Value("${app.cors.allow-dev-localhost:true}")
    private boolean allowDevLocalhost;

    /**
     * Déclare la chaîne de filtres HTTP, les autorisations par chemin et l'ordre des filtres.
     *
     * @param http builder de configuration HTTP Security
     * @return chaîne de filtres construite
     * @throws Exception en cas d'erreur de configuration Spring Security
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .headers(headers -> {
                    headers.contentTypeOptions(Customizer.withDefaults());
                    headers.frameOptions(frame -> frame.deny());
                    headers.referrerPolicy(referrer ->
                            referrer.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN));
                    headers.permissionsPolicy(permissions ->
                            permissions.policy("camera=(), microphone=(), geolocation=()"));
                    headers.contentSecurityPolicy(csp ->
                            csp.policyDirectives("default-src 'none'; frame-ancestors 'none'; base-uri 'none'"));
                    if (environment.matchesProfiles("production")) {
                        headers.httpStrictTransportSecurity(hsts ->
                                hsts.includeSubDomains(true).maxAgeInSeconds(31_536_000L));
                    }
                })
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/api/auth/login",
                                "/api/auth/refresh",
                                "/api/auth/logout").permitAll()
                        .requestMatchers(
                                "/v3/api-docs",
                                "/v3/api-docs/**",
                                "/swagger-ui/**",
                                "/swagger-ui.html").permitAll()
                        .requestMatchers("/actuator/health", "/actuator/health/**", "/actuator/info").permitAll()
                        .requestMatchers("/uploads/**").permitAll()
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers("/api/auth/change-password").authenticated()
                        .requestMatchers("/api/users", "/api/users/**").hasRole(Roles.ADMIN)
                        .requestMatchers("/api/setup", "/api/setup/**").hasRole(Roles.ADMIN)
                        .requestMatchers("/api/logs", "/api/logs/**").hasRole(Roles.ADMIN)
                        .requestMatchers("/api/ateliers", "/api/ateliers/**")
                            .hasAnyRole(Roles.ADMIN, Roles.TECHNICIEN)
                        .requestMatchers("/api/devices", "/api/devices/**")
                            .hasAnyRole(Roles.ADMIN, Roles.TECHNICIEN)
                        .requestMatchers("/api/sfm", "/api/sfm/**", "/api/mas", "/api/mas/**")
                            .hasAnyRole(Roles.ADMIN, Roles.TECHNICIEN)
                        .requestMatchers("/api/order-requests", "/api/order-requests/**")
                            .hasAnyRole(Roles.ADMIN, Roles.TECHNICIEN)
                        .requestMatchers("/api/ai", "/api/ai/**")
                            .hasAnyRole(Roles.ADMIN, Roles.TECHNICIEN)
                        .anyRequest().hasAnyRole(Roles.ADMIN, Roles.TECHNICIEN))
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(loginRateLimitFilter, JwtAuthenticationFilter.class)
                .addFilterAfter(atelierContextFilter, JwtAuthenticationFilter.class);
        return http.build();
    }

    /**
     * Configuration CORS : origines exactes depuis {@code app.cors.allowed-origins},
     * plus localhost optionnel en développement. Jamais de motif {@code *.onrender.com}.
     *
     * @return source CORS enregistrée sur {@code /**}
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        List<String> configured = List.of(allowedOrigins.split(",")).stream()
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .map(s -> s.endsWith("/") ? s.substring(0, s.length() - 1) : s)
                .toList();

        LinkedHashSet<String> patterns = new LinkedHashSet<>(configured);
        if (allowDevLocalhost) {
            patterns.add("http://localhost:*");
            patterns.add("http://127.0.0.1:*");
        }

        config.setAllowedOriginPatterns(new ArrayList<>(patterns));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setExposedHeaders(List.of("Authorization"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    /**
     * Encodeur de mots de passe BCrypt pour la création et la vérification des comptes.
     *
     * @return instance {@link BCryptPasswordEncoder}
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * Fournit le gestionnaire d'authentification Spring Security.
     *
     * @param configuration configuration d'authentification Spring
     * @return gestionnaire d'authentification
     * @throws Exception en cas d'erreur d'initialisation
     */
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
        return configuration.getAuthenticationManager();
    }
}
