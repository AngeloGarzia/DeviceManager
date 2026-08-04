package com.devicemanager.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Requête de message envoyé à l'assistant IA intégré.
 */
@Data
public class AiChatRequest {

    @NotBlank
    @Size(max = 4000)
    private String message;
}
