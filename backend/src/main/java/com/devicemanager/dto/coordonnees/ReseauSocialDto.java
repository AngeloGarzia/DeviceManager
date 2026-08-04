package com.devicemanager.dto.coordonnees;

import com.devicemanager.entity.coordonnees.TypeReseauSocial;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ReseauSocialDto {
    private Long id;
    private TypeReseauSocial type;

    @Size(max = 255)
    private String url;
}
