package com.devicemanager.tenancy;

import com.devicemanager.entity.Atelier;
import com.devicemanager.entity.User;
import com.devicemanager.repository.AtelierRepository;
import com.devicemanager.repository.UserRepository;
import com.devicemanager.security.Roles;
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

/**
 * Filtre de multi-location (tenancy) par atelier, exécuté après l'authentification JWT.
 * <p>
 * Le frontend envoie l'atelier actif via l'en-tête HTTP {@value #HEADER}. Lorsque cet en-tête
 * est présent et que l'utilisateur est authentifié :
 * <ul>
 *   <li>l'atelier est chargé et vérifié (existence, appartenance au même groupe que l'utilisateur) ;</li>
 *   <li>les techniciens ne peuvent sélectionner que leur atelier préféré ({@code preferredAtelier}) ;</li>
 *   <li>l'identifiant est exposé aux services via {@link AtelierContext} pour la durée de la requête.</li>
 * </ul>
 * Sans en-tête, la requête continue sans contexte d'atelier (certains endpoints peuvent exiger
 * {@link AtelierContext#require()} en aval).
 * <p>
 * Ignoré pour {@code /api/auth/**} et {@code /uploads/**}. En fin de requête, le contexte
 * thread-local est toujours nettoyé dans un bloc {@code finally}.
 */
@Component
@RequiredArgsConstructor
public class AtelierContextFilter extends OncePerRequestFilter {

    /** Nom de l'en-tête HTTP portant l'identifiant numérique de l'atelier sélectionné. */
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
                    if (isTechnicien(user.getRole())) {
                        Atelier preferred = user.getPreferredAtelier();
                        if (preferred == null || !preferred.getId().equals(atelierId)) {
                            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                                    "Les techniciens sont limités à leur atelier préféré");
                        }
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

    private static boolean isTechnicien(String role) {
        return Roles.TECHNICIEN.equals(role) || "TECH".equals(role);
    }
}
