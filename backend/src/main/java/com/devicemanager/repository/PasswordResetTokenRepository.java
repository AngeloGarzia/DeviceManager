package com.devicemanager.repository;

import com.devicemanager.entity.PasswordResetToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

/**
 * Persistance des jetons de réinitialisation de mot de passe.
 */
public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {

    /**
     * Charge un jeton non utilisé par son hash, avec l'utilisateur associé.
     *
     * @param tokenHash hash SHA-256 du jeton opaque
     * @return jeton trouvé ou vide
     */
    @Query("""
            SELECT t FROM PasswordResetToken t
            JOIN FETCH t.user
            WHERE t.tokenHash = :tokenHash AND t.used = false
            """)
    Optional<PasswordResetToken> findActiveByTokenHash(@Param("tokenHash") String tokenHash);

    /**
     * Invalide tous les jetons non utilisés d'un utilisateur (nouvelle demande).
     *
     * @param userId identifiant utilisateur
     * @return nombre de jetons invalidés
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE PasswordResetToken t SET t.used = true WHERE t.user.id = :userId AND t.used = false")
    int invalidateUnusedByUserId(@Param("userId") Long userId);
}
