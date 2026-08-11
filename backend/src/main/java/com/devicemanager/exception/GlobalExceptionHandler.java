package com.devicemanager.exception;

import com.devicemanager.dto.ApiError;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.stream.Collectors;

/**
 * Gestionnaire global des exceptions REST.
 * Transforme les erreurs applicatives en réponses {@link ApiError} structurées,
 * avec des messages métier Device Manager (jamais de détail technique brut).
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final String MSG_INTERNAL =
            "Une erreur interne est survenue dans Device Manager. Réessayez ou contactez un administrateur.";
    private static final String MSG_ATELIER_REQUIS =
            "Sélectionnez un atelier pour continuer.";

    /**
     * Convertit une {@link ResponseStatusException} en réponse HTTP avec le statut et le message fournis.
     *
     * @param ex      exception avec code HTTP et raison
     * @param request requête en cours (pour le chemin)
     * @return réponse d'erreur structurée
     */
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiError> handleStatus(ResponseStatusException ex, HttpServletRequest request) {
        String message = ex.getReason();
        if (message == null || message.isBlank()) {
            message = defaultReason(ex.getStatusCode().value());
        }
        return build(ex.getStatusCode().value(), message, request.getRequestURI());
    }

    /**
     * Agrège les messages de validation Bean Validation en une seule réponse 400.
     *
     * @param ex      exception de validation des arguments du contrôleur
     * @param request requête en cours (pour le chemin)
     * @return réponse d'erreur avec les messages de champ concaténés
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(this::fieldErrorMessage)
                .collect(Collectors.joining("; "));
        if (message.isBlank()) {
            message = "Les informations saisies sont incomplètes ou invalides.";
        }
        return build(HttpStatus.BAD_REQUEST.value(), message, request.getRequestURI());
    }

    /**
     * Traduit les violations d'intégrité SQL en messages métier lisibles (unicité, clés étrangères).
     *
     * @param ex      exception de contrainte base de données
     * @param request requête en cours (pour le chemin)
     * @return réponse 409 avec message contextualisé
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiError> handleIntegrity(DataIntegrityViolationException ex, HttpServletRequest request) {
        String raw = ex.getMostSpecificCause().getMessage();
        String lower = raw == null ? "" : raw.toLowerCase();
        String message;
        // FK d'abord : le message MySQL contient "REFERENCES", qui ne doit pas
        // être confondu avec l'unicité de la colonne device.reference.
        if (lower.contains("foreign key")
                || lower.contains("cannot delete or update a parent row")
                || lower.contains("a foreign key constraint fails")) {
            message = "Suppression impossible : cet élément est encore utilisé "
                    + "(par ex. une pièce liée à une demande de commande).";
        } else if (lower.contains("uk_mas_numero") || lower.contains("numero")) {
            message = "Numéro MAS déjà utilisé";
        } else if (lower.contains("uk_sfm_nom") || (lower.contains("sfm") && lower.contains("nom"))) {
            message = "Nom SFM déjà utilisé";
        } else if (lower.contains("uk_device_nom") || (lower.contains("device") && lower.contains("nom"))) {
            message = "Nom de pièce déjà utilisé";
        } else if (lower.contains("uk_device_reference")
                || (lower.contains("duplicate") && lower.contains("reference"))) {
            message = "Référence déjà utilisée";
        } else if (lower.contains("marque") || lower.contains("label") || lower.contains("code")) {
            message = "Nom de marque déjà utilisé";
        } else if (lower.contains("constraint")) {
            message = "Suppression impossible : cet élément est encore utilisé ailleurs.";
        } else {
            message = "Cette valeur existe déjà. Vérifiez le nom, la référence ou le numéro saisi.";
        }
        return build(HttpStatus.CONFLICT.value(), message, request.getRequestURI());
    }

    /**
     * Atelier non sélectionné (contexte tenancy).
     */
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ApiError> handleIllegalState(IllegalStateException ex, HttpServletRequest request) {
        String msg = ex.getMessage() == null ? "" : ex.getMessage();
        if (msg.toLowerCase().contains("atelier") || msg.startsWith("Sélectionnez")) {
            return build(HttpStatus.BAD_REQUEST.value(), MSG_ATELIER_REQUIS, request.getRequestURI());
        }
        if (!msg.isBlank() && !looksTechnical(msg)) {
            log.warn("État incohérent sur {}: {}", request.getRequestURI(), msg);
            return build(HttpStatus.INTERNAL_SERVER_ERROR.value(), msg, request.getRequestURI());
        }
        log.error("État incohérent sur {}: {}", request.getRequestURI(), msg, ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR.value(), MSG_INTERNAL, request.getRequestURI());
    }

    /**
     * Attrape toute exception non gérée et renvoie une erreur 500 métier (sans détail technique).
     *
     * @param ex      exception non prévue
     * @param request requête en cours (pour le chemin)
     * @return réponse d'erreur interne
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleGeneric(Exception ex, HttpServletRequest request) {
        log.error("Erreur non gérée sur {}: {}", request.getRequestURI(), ex.getMessage(), ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR.value(), MSG_INTERNAL, request.getRequestURI());
    }

    private String fieldErrorMessage(FieldError error) {
        String msg = error.getDefaultMessage();
        if (msg == null || msg.isBlank()
                || msg.startsWith("must ")
                || msg.contains("must not")
                || msg.contains("size must")) {
            return "Le champ « " + error.getField() + " » est invalide ou obligatoire.";
        }
        return msg;
    }

    private static boolean looksTechnical(String message) {
        String lower = message.toLowerCase();
        return lower.contains("exception")
                || lower.contains("nullpointer")
                || lower.contains("hibernate")
                || lower.contains("sql")
                || lower.contains("jdbc")
                || lower.contains("docker")
                || lower.contains(".env")
                || lower.contains("jwt")
                || lower.contains("stack trace")
                || lower.contains("caused by");
    }

    private static String defaultReason(int status) {
        return switch (status) {
            case 400 -> "Requête invalide.";
            case 401 -> "Authentification requise. Veuillez vous reconnecter.";
            case 403 -> "Vous n'avez pas les droits pour cette action.";
            case 404 -> "Élément introuvable.";
            case 409 -> "Cette action entre en conflit avec l'état actuel.";
            default -> MSG_INTERNAL;
        };
    }

    private ResponseEntity<ApiError> build(int status, String message, String path) {
        ApiError body = ApiError.builder()
                .timestamp(Instant.now())
                .status(status)
                .error(HttpStatus.valueOf(status).getReasonPhrase())
                .message(message)
                .path(path)
                .build();
        return ResponseEntity.status(status).body(body);
    }
}
