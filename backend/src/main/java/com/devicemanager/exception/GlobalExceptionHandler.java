package com.devicemanager.exception;

import com.devicemanager.dto.ApiError;
import jakarta.persistence.EntityNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.exception.JDBCConnectionException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.jdbc.CannotGetJdbcConnectionException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
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
    private static final String MSG_DB_UNAVAILABLE =
            "La base de données est temporairement indisponible "
                    + "(connexion interrompue ou serveur en veille). Réessayez dans quelques instants.";

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
     * Connexion MySQL / pool indisponible (idle stale, cold start, Aiven coupé, timeout socket).
     */
    @ExceptionHandler({
            CannotGetJdbcConnectionException.class,
            DataAccessResourceFailureException.class,
            JDBCConnectionException.class,
            QueryTimeoutException.class
    })
    public ResponseEntity<ApiError> handleDatabaseUnavailable(Exception ex, HttpServletRequest request) {
        log.error("Base de données indisponible sur {}: {}", request.getRequestURI(), ex.getMessage(), ex);
        return build(HttpStatus.SERVICE_UNAVAILABLE.value(), MSG_DB_UNAVAILABLE, request.getRequestURI());
    }

    /**
     * Argument métier invalide (par exemple clé de stockage vide) : renvoie 400 sans exposer la stack.
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiError> handleIllegalArgument(IllegalArgumentException ex, HttpServletRequest request) {
        String msg = ex.getMessage();
        if (msg == null || msg.isBlank() || looksTechnical(msg)) {
            msg = "Requête invalide : vérifiez les informations transmises.";
        }
        log.debug("Argument invalide sur {}: {}", request.getRequestURI(), ex.getMessage());
        return build(HttpStatus.BAD_REQUEST.value(), msg, request.getRequestURI());
    }

    /**
     * Corps JSON absent, malformé ou incompatible avec le DTO cible.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleNotReadable(HttpMessageNotReadableException ex, HttpServletRequest request) {
        log.debug("Corps de requête illisible sur {}: {}", request.getRequestURI(), ex.getMessage());
        return build(HttpStatus.BAD_REQUEST.value(),
                "Le corps de la requête est absent ou mal formé. Vérifiez le format JSON attendu.",
                request.getRequestURI());
    }

    /**
     * Champ multipart obligatoire manquant (upload de fichier absent).
     */
    @ExceptionHandler(MissingServletRequestPartException.class)
    public ResponseEntity<ApiError> handleMissingPart(MissingServletRequestPartException ex, HttpServletRequest request) {
        log.debug("Multipart manquant sur {}: {}", request.getRequestURI(), ex.getMessage());
        return build(HttpStatus.BAD_REQUEST.value(),
                "Fichier requis manquant : « " + ex.getRequestPartName() + " ».",
                request.getRequestURI());
    }

    /**
     * Paramètre de requête obligatoire absent.
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiError> handleMissingParam(MissingServletRequestParameterException ex,
                                                      HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST.value(),
                "Paramètre requis manquant : « " + ex.getParameterName() + " ».",
                request.getRequestURI());
    }

    /**
     * Paramètre de type incompatible dans l'URL (ex. attendait un {@code Long}, reçu {@code "abc"}).
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiError> handleTypeMismatch(MethodArgumentTypeMismatchException ex,
                                                      HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST.value(),
                "Paramètre « " + ex.getName() + " » invalide : format inattendu.",
                request.getRequestURI());
    }

    /**
     * Violation de contrainte Bean Validation hors {@code @RequestBody} (ex. {@code @RequestParam} annoté).
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiError> handleConstraintViolation(ConstraintViolationException ex,
                                                              HttpServletRequest request) {
        String message = ex.getConstraintViolations().stream()
                .map(v -> v.getPropertyPath() + " : " + v.getMessage())
                .collect(Collectors.joining("; "));
        if (message.isBlank()) {
            message = "Requête invalide : contraintes non respectées.";
        }
        return build(HttpStatus.BAD_REQUEST.value(), message, request.getRequestURI());
    }

    /**
     * Entité JPA introuvable (par exemple {@code getReferenceById} suivi d'un accès).
     */
    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<ApiError> handleEntityNotFound(EntityNotFoundException ex, HttpServletRequest request) {
        log.debug("Entité introuvable sur {}: {}", request.getRequestURI(), ex.getMessage());
        return build(HttpStatus.NOT_FOUND.value(), "Élément introuvable.", request.getRequestURI());
    }

    /**
     * Méthode HTTP non supportée par l'endpoint.
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiError> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex,
                                                             HttpServletRequest request) {
        return build(HttpStatus.METHOD_NOT_ALLOWED.value(),
                "Méthode HTTP non autorisée pour ce point d'accès.",
                request.getRequestURI());
    }

    /**
     * Type de contenu non supporté (ex. XML envoyé sur un endpoint JSON).
     */
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiError> handleMediaTypeNotSupported(HttpMediaTypeNotSupportedException ex,
                                                                HttpServletRequest request) {
        return build(HttpStatus.UNSUPPORTED_MEDIA_TYPE.value(),
                "Format de contenu non pris en charge.",
                request.getRequestURI());
    }

    /**
     * Upload trop volumineux : dépassement de la taille maximale de fichier configurée.
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiError> handleMaxUploadSize(MaxUploadSizeExceededException ex, HttpServletRequest request) {
        log.debug("Upload trop volumineux sur {}", request.getRequestURI());
        return build(HttpStatus.PAYLOAD_TOO_LARGE.value(),
                "Fichier trop volumineux. Réduisez sa taille ou compressez-le avant de réessayer.",
                request.getRequestURI());
    }

    /**
     * Stockage cloud (R2/S3) injoignable, refusé (auth) ou en panne serveur : renvoyer 503
     * afin de ne pas masquer l'erreur en 404 métier.
     */
    @ExceptionHandler(StorageUnavailableException.class)
    public ResponseEntity<ApiError> handleStorageUnavailable(StorageUnavailableException ex,
                                                             HttpServletRequest request) {
        log.error("Stockage cloud indisponible sur {}: {}", request.getRequestURI(), ex.getMessage(), ex);
        return build(HttpStatus.SERVICE_UNAVAILABLE.value(),
                ex.getMessage() == null || ex.getMessage().isBlank()
                        ? "Stockage cloud temporairement indisponible."
                        : ex.getMessage(),
                request.getRequestURI());
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
