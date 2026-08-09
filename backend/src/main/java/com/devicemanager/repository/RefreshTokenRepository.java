package com.devicemanager.repository;

import com.devicemanager.entity.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

/**
 * Persistance des jetons de rafraîchissement.
 */
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    /**
     * Charge un jeton non révoqué par son hash, avec l'utilisateur associé.
     *
     * @param tokenHash hash SHA-256 du jeton opaque
     * @return jeton trouvé ou vide
     */
    @Query("""
            SELECT r FROM RefreshToken r
            JOIN FETCH r.user u
            LEFT JOIN FETCH u.groupe
            LEFT JOIN FETCH u.preferredAtelier
            WHERE r.tokenHash = :tokenHash AND r.revoked = false
            """)
    Optional<RefreshToken> findActiveByTokenHash(@Param("tokenHash") String tokenHash);
}
