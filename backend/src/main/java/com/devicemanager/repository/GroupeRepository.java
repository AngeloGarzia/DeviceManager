package com.devicemanager.repository;

import com.devicemanager.entity.Groupe;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface GroupeRepository extends JpaRepository<Groupe, Long> {
    Optional<Groupe> findByNomIgnoreCase(String nom);
}
