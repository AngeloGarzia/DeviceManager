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

@Configuration
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final AtelierContextFilter atelierContextFilter;

    @Value("${app.cors.allowed-origins}")
    private String allowedOrigins;

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
                        .requestMatchers("/api/ateliers", "/api/ateliers/**")
                            .hasAnyRole(Roles.ADMIN, Roles.TECHNICIEN)
                        .requestMatchers("/api/devices", "/api/devices/**")
                            .hasAnyRole(Roles.ADMIN, Roles.TECHNICIEN)
                        .requestMatchers("/api/sfm", "/api/sfm/**", "/api/mas", "/api/mas/**")
                            .hasAnyRole(Roles.ADMIN, Roles.TECHNICIEN)
                        .requestMatchers("/api/order-requests", "/api/order-requests/**")
                            .hasAnyRole(Roles.ADMIN, Roles.TECHNICIEN)
                        .anyRequest().hasAnyRole(Roles.ADMIN, Roles.TECHNICIEN))
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(atelierContextFilter, JwtAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        List<String> origins = List.of(allowedOrigins.split(",")).stream()
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .toList();
        if (origins.isEmpty()) {
            // Fallback prod/demo : autorise les fronts Render si la variable n'est pas renseignée
            config.setAllowedOriginPatterns(List.of(
                    "https://*.onrender.com",
                    "http://localhost:*",
                    "http://127.0.0.1:*"
            ));
        } else {
            config.setAllowedOrigins(origins);
        }
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
        return configuration.getAuthenticationManager();
    }
}
