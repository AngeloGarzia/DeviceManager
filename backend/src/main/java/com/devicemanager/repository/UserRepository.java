package com.devicemanager.repository;

import com.devicemanager.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    @Query("""
            SELECT u FROM User u
            LEFT JOIN FETCH u.groupe
            LEFT JOIN FETCH u.preferredAtelier
            WHERE u.username = :username
            """)
    Optional<User> findByUsername(@Param("username") String username);

    boolean existsByUsername(String username);

    boolean existsByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCaseAndIdNot(String email, Long id);

    @Modifying
    @Query("UPDATE User u SET u.preferredAtelier = NULL WHERE u.preferredAtelier.id = :atelierId")
    int clearPreferredAtelier(@Param("atelierId") Long atelierId);

    @Query("""
            SELECT u FROM User u
            LEFT JOIN FETCH u.groupe
            WHERE u.groupe.id = :groupeId
            ORDER BY u.nom, u.prenom, u.username
            """)
    List<User> findAllByGroupeId(@Param("groupeId") Long groupeId);

    @Query("""
            SELECT u FROM User u
            LEFT JOIN FETCH u.groupe
            WHERE u.id IN :ids
            """)
    List<User> findAllByIdInWithGroupe(@Param("ids") Collection<Long> ids);
}
