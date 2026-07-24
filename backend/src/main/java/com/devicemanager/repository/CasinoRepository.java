package com.devicemanager.repository;

import com.devicemanager.entity.Casino;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CasinoRepository extends JpaRepository<Casino, Long> {
    List<Casino> findByGroupeIdOrderByNomAsc(Long groupeId);

    Optional<Casino> findByNomIgnoreCaseAndGroupeId(String nom, Long groupeId);
}
