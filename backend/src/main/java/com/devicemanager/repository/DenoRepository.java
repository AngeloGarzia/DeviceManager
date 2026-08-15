package com.devicemanager.repository;

import com.devicemanager.entity.Deno;
import org.springframework.data.jpa.repository.JpaRepository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * Accès au référentiel des dénominations MAS.
 */
public interface DenoRepository extends JpaRepository<Deno, Long> {

    List<Deno> findAllByOrderByValeurAsc();

    boolean existsByValeur(BigDecimal valeur);

    boolean existsByLabelIgnoreCase(String label);

    Optional<Deno> findByValeur(BigDecimal valeur);
}
