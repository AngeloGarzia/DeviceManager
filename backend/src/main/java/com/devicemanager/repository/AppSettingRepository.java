package com.devicemanager.repository;

import com.devicemanager.entity.AppSetting;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Accès aux paramètres applicatifs clé-valeur ({@link AppSetting}).
 */
public interface AppSettingRepository extends JpaRepository<AppSetting, String> {

    /**
     * Retourne tous les paramètres triés par catégorie puis libellé.
     *
     * @return liste ordonnée des paramètres
     */
    List<AppSetting> findAllByOrderByCategoryAscLabelAsc();
}
