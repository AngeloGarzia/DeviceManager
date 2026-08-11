package com.devicemanager.repository;

import com.devicemanager.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Accès aux comptes utilisateurs ({@link User}).
 */
public interface UserRepository extends JpaRepository<User, Long> {

    /**
     * Charge un utilisateur par nom d'utilisateur avec groupe et atelier préféré pré-chargés.
     *
     * @param username identifiant de connexion
     * @return utilisateur trouvé ou vide
     */
    @Query("""
            SELECT u FROM User u
            LEFT JOIN FETCH u.groupe
            LEFT JOIN FETCH u.preferredAtelier
            WHERE u.username = :username
            """)
    Optional<User> findByUsername(@Param("username") String username);

    /**
     * Vérifie l'existence d'un nom d'utilisateur.
     *
     * @param username identifiant à vérifier
     * @return {@code true} si le nom d'utilisateur existe déjà
     */
    boolean existsByUsername(String username);

    /**
     * Vérifie l'existence d'une adresse e-mail (insensible à la casse).
     *
     * @param email e-mail à vérifier
     * @return {@code true} si l'e-mail existe déjà
     */
    boolean existsByEmailIgnoreCase(String email);

    /**
     * Vérifie l'existence d'une autre adresse e-mail (hors l'identifiant donné).
     *
     * @param email e-mail à vérifier
     * @param id    identifiant de l'utilisateur à exclure (mise à jour)
     * @return {@code true} si l'e-mail est déjà utilisé
     */
    boolean existsByEmailIgnoreCaseAndIdNot(String email, Long id);

    /**
     * Réinitialise l'atelier préféré de tous les utilisateurs pointant vers l'atelier supprimé.
     *
     * @param atelierId identifiant de l'atelier supprimé
     * @return nombre d'utilisateurs mis à jour
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE User u SET u.preferredAtelier = NULL WHERE u.preferredAtelier.id = :atelierId")
    int clearPreferredAtelier(@Param("atelierId") Long atelierId);

    /**
     * Liste les utilisateurs d'un groupe triés par nom, prénom et identifiant.
     *
     * @param groupeId identifiant du groupe
     * @return utilisateurs du groupe
     */
    @Query("""
            SELECT u FROM User u
            LEFT JOIN FETCH u.groupe
            WHERE u.groupe.id = :groupeId
            ORDER BY u.nom, u.prenom, u.username
            """)
    List<User> findAllByGroupeId(@Param("groupeId") Long groupeId);

    /**
     * Charge plusieurs utilisateurs par identifiants avec leur groupe pré-chargé.
     *
     * @param ids identifiants des utilisateurs
     * @return utilisateurs trouvés
     */
    @Query("""
            SELECT u FROM User u
            LEFT JOIN FETCH u.groupe
            WHERE u.id IN :ids
            """)
    List<User> findAllByIdInWithGroupe(@Param("ids") Collection<Long> ids);

    /**
     * Liste les utilisateurs ayant cet atelier comme atelier préféré.
     *
     * @param atelierId identifiant de l'atelier
     * @return utilisateurs concernés, triés par nom
     */
    @Query("""
            SELECT u FROM User u
            LEFT JOIN FETCH u.groupe
            WHERE u.preferredAtelier.id = :atelierId
            ORDER BY u.nom, u.prenom, u.username
            """)
    List<User> findAllByPreferredAtelierId(@Param("atelierId") Long atelierId);
}
