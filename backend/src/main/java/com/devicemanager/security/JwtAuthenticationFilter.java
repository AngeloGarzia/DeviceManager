package com.devicemanager.security;

import com.devicemanager.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Filtre d'authentification JWT exécuté avant la chaîne Spring Security.
 * <p>
 * Lit l'en-tête {@code Authorization: Bearer &lt;token&gt;}, valide le JWT via {@link JwtService},
 * charge l'utilisateur en base et peuple le {@link SecurityContextHolder} avec son rôle
 * ({@code ROLE_ADMIN} ou {@code ROLE_TECHNICIEN}).
 * <p>
 * Un token absent, invalide ou expiré laisse la requête non authentifiée : les endpoints protégés
 * seront refusés par {@link com.devicemanager.config.SecurityConfig}. Ce filtre s'exécute
 * <em>avant</em> {@link com.devicemanager.tenancy.AtelierContextFilter}, qui dépend d'un
 * utilisateur déjà authentifié pour résoudre le contexte d'atelier.
 */
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UserRepository userRepository;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = header.substring(7);
        try {
            String username = jwtService.extractUsername(token);
            if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                userRepository.findByUsername(username).ifPresent(user -> {
                    if (jwtService.isTokenValid(token, user.getUsername())) {
                        var auth = new UsernamePasswordAuthenticationToken(
                                user.getUsername(),
                                null,
                                List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole())));
                        auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                        SecurityContextHolder.getContext().setAuthentication(auth);
                    }
                });
            }
        } catch (Exception ignored) {
            // Token invalide : laisser Spring Security refuser l'accès
        }

        filterChain.doFilter(request, response);
    }
}
