package com.devicemanager.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "app_setting")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AppSetting {

    @Id
    @Column(name = "setting_key", length = 80)
    private String settingKey;

    @Column(name = "setting_value", length = 1000)
    private String settingValue;

    @Column(nullable = false, length = 160)
    private String label;

    @Column(nullable = false, length = 40)
    private String category;

    @Column(name = "secret_value", nullable = false)
    private boolean secretValue;
}
