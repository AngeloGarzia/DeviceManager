package com.devicemanager.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Requête de message envoyé à l'assistant IA intégré.
 */
@Data
public class AiChatRequest {

    @NotBlank(message = "Le message pour l'assistant IA est obligatoire")
    @Size(max = 4000, message = "Le message ne doit pas dépasser 4000 caractères")
    private String message;
}
