package com.devicemanager.repository;

import com.devicemanager.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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
}
