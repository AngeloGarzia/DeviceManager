package com.devicemanager.repository;

import com.devicemanager.entity.AtelierMemoireSynaptique;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AtelierMemoireSynaptiqueRepository extends JpaRepository<AtelierMemoireSynaptique, Long> {

    Optional<AtelierMemoireSynaptique> findByAtelierId(Long atelierId);
}
