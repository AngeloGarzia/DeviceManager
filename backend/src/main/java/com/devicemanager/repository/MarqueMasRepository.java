package com.devicemanager.repository;

import com.devicemanager.entity.MarqueMas;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MarqueMasRepository extends JpaRepository<MarqueMas, Long> {
    List<MarqueMas> findAllByOrderByLabelAsc();

    Optional<MarqueMas> findByCodeIgnoreCase(String code);

    Optional<MarqueMas> findByLabelIgnoreCase(String label);

    boolean existsByCodeIgnoreCase(String code);

    boolean existsByLabelIgnoreCase(String label);
}
