package com.devicemanager.config;

import com.devicemanager.security.JwtAuthenticationFilter;
import com.devicemanager.security.Roles;
import com.devicemanager.tenancy.AtelierContextFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
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
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * Configuration Spring Security : API stateless JWT, CORS et règles d'accès par rôle.
 * <p>
 * Chaîne de filtres : {@link JwtAuthenticationFilter} (Bearer token) puis
 * {@link AtelierContextFilter} (en-tête {@code X-Atelier-Id}). CSRF désactivé (API REST).
 * Sessions désactivées ({@link SessionCreationPolicy#STATELESS}).
 * <p>
 * Accès public : authentification ({@code /api/auth/**}), santé Actuator, fichiers uploadés
 * et requêtes OPTIONS. Gestion des utilisateurs, setup et logs réservés aux admins ; le reste de
 * l'API métier exige le rôle admin ou technicien.
 */
@Configuration
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final AtelierContextFilter atelierContextFilter;

    @Value("${app.cors.allowed-origins}")
    private String allowedOrigins;

    /**
     * Déclare la chaîne de filtres HTTP, les autorisations par chemin et l'ordre JWT → atelier.
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
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/auth/**").permitAll()
                        .requestMatchers("/actuator/health", "/actuator/health/**", "/actuator/info").permitAll()
                        .requestMatchers("/uploads/**").permitAll()
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
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
                .addFilterAfter(atelierContextFilter, JwtAuthenticationFilter.class);
        return http.build();
    }

    /**
     * Configuration CORS : origines depuis {@code app.cors.allowed-origins}, plus Render,
     * localhost et 127.0.0.1 par motif. Credentials et en-tête {@code Authorization} exposés.
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

        // Toujours autoriser les fronts Render (+ locaux), en plus des origines configurées.
        java.util.LinkedHashSet<String> patterns = new java.util.LinkedHashSet<>();
        patterns.addAll(configured);
        patterns.add("https://*.onrender.com");
        patterns.add("http://localhost:*");
        patterns.add("http://127.0.0.1:*");

        config.setAllowedOriginPatterns(new java.util.ArrayList<>(patterns));
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
