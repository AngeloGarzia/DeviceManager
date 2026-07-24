package com.devicemanager.tenancy;

import com.devicemanager.entity.Atelier;
import com.devicemanager.entity.User;
import com.devicemanager.repository.AtelierRepository;
import com.devicemanager.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class AtelierContextFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Atelier-Id";

    private final AtelierRepository atelierRepository;
    private final UserRepository userRepository;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.startsWith("/api/auth/") || path.startsWith("/uploads/");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.isAuthenticated() && auth.getPrincipal() instanceof String username) {
                String raw = request.getHeader(HEADER);
                if (raw != null && !raw.isBlank()) {
                    Long atelierId = Long.parseLong(raw.trim());
                    User user = userRepository.findByUsername(username)
                            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
                    Atelier atelier = atelierRepository.findByIdWithCasino(atelierId)
                            .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Atelier introuvable"));
                    if (user.getGroupe() == null
                            || atelier.getCasino() == null
                            || atelier.getCasino().getGroupe() == null
                            || !user.getGroupe().getId().equals(atelier.getCasino().getGroupe().getId())) {
                        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Atelier non autorisé pour ce compte");
                    }
                    AtelierContext.set(atelierId);
                }
            }
            filterChain.doFilter(request, response);
        } catch (ResponseStatusException ex) {
            response.setStatus(ex.getStatusCode().value());
            response.setContentType("application/json");
            response.getWriter().write("{\"message\":\"" + (ex.getReason() == null ? "Erreur atelier" : ex.getReason()) + "\"}");
        } catch (NumberFormatException ex) {
            response.setStatus(HttpStatus.BAD_REQUEST.value());
            response.setContentType("application/json");
            response.getWriter().write("{\"message\":\"Identifiant atelier invalide\"}");
        } finally {
            AtelierContext.clear();
        }
    }
}
