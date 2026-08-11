package com.devicemanager.controller;

import com.devicemanager.dto.AuthResponse;
import com.devicemanager.dto.ChangePasswordRequest;
import com.devicemanager.dto.LoginRequest;
import com.devicemanager.security.JwtService;
import com.devicemanager.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

/**
 * Contrôleur REST d'authentification pour DeviceManager.
 * <p>
 * Gère la connexion JWT (access token dans le corps, refresh token en cookie HttpOnly),
 * le rafraîchissement, la déconnexion et le changement de mot de passe.
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    static final String REFRESH_COOKIE = "dm_refresh";

    private final AuthService authService;
    private final JwtService jwtService;

    /**
     * Authentifie un utilisateur et retourne un jeton d'accès ; pose le cookie refresh.
     *
     * @param request     identifiants de connexion
     * @param httpRequest requête HTTP (schéma / proxy)
     * @return réponse contenant le jeton, le rôle, le groupe et l'atelier par défaut
     */
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest) {
        AuthService.AuthSession session = authService.login(request);
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, refreshCookie(session.refreshToken(), httpRequest).toString())
                .body(session.response());
    }

    /**
     * Renouvelle la session à partir du cookie refresh (rotation).
     *
     * @param refreshToken cookie {@code dm_refresh}
     * @param httpRequest  requête HTTP
     * @return nouvelle réponse d'authentification
     */
    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(
            @CookieValue(name = REFRESH_COOKIE, required = false) String refreshToken,
            HttpServletRequest httpRequest) {
        AuthService.AuthSession session = authService.refresh(refreshToken);
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, refreshCookie(session.refreshToken(), httpRequest).toString())
                .body(session.response());
    }

    /**
     * Révoque le refresh token et efface le cookie.
     *
     * @param refreshToken cookie {@code dm_refresh}
     * @param httpRequest  requête HTTP
     * @return 204 No Content
     */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @CookieValue(name = REFRESH_COOKIE, required = false) String refreshToken,
            HttpServletRequest httpRequest) {
        authService.logout(refreshToken);
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, clearRefreshCookie(httpRequest).toString())
                .build();
    }

    /**
     * Change le mot de passe de l'utilisateur authentifié.
     *
     * @param authentication principal Spring Security
     * @param request        mots de passe actuel et nouveau
     * @return 204 No Content
     */
    @PostMapping("/change-password")
    public ResponseEntity<Void> changePassword(
            Authentication authentication,
            @Valid @RequestBody ChangePasswordRequest request) {
        authService.changePassword(authentication.getName(), request);
        return ResponseEntity.noContent().build();
    }

    private ResponseCookie refreshCookie(String rawToken, HttpServletRequest request) {
        boolean secure = isSecureRequest(request);
        // Prod front/API cross-origin : SameSite=None + Secure pour que le cookie refresh parte en XHR.
        // Dev same-origin (proxy) : Lax suffit sur HTTP local.
        return ResponseCookie.from(REFRESH_COOKIE, rawToken)
                .httpOnly(true)
                .secure(secure)
                .path("/api/auth")
                .maxAge(Duration.ofMillis(jwtService.getRefreshExpirationMs()))
                .sameSite(secure ? "None" : "Lax")
                .build();
    }

    private ResponseCookie clearRefreshCookie(HttpServletRequest request) {
        boolean secure = isSecureRequest(request);
        return ResponseCookie.from(REFRESH_COOKIE, "")
                .httpOnly(true)
                .secure(secure)
                .path("/api/auth")
                .maxAge(0)
                .sameSite(secure ? "None" : "Lax")
                .build();
    }

    static boolean isSecureRequest(HttpServletRequest request) {
        if (request.isSecure()) {
            return true;
        }
        String forwarded = request.getHeader("X-Forwarded-Proto");
        return forwarded != null && "https".equalsIgnoreCase(forwarded.trim());
    }
}
