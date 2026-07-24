package com.devicemanager.repository;

import com.devicemanager.entity.AppSetting;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AppSettingRepository extends JpaRepository<AppSetting, String> {
    List<AppSetting> findAllByOrderByCategoryAscLabelAsc();
}
