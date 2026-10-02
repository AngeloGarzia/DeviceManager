package com.devicemanager.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * Requête de message envoyé à l'assistant IA intégré (avec historique multi-tours).
 */
@Data
public class AiChatRequest {

    @NotBlank(message = "Le message pour l'assistant IA est obligatoire")
    @Size(max = 4000, message = "Le message ne doit pas dépasser 4000 caractères")
    private String message;

    /** Tours précédents (user/assistant), hors message courant. */
    @Valid
    @Size(max = 40, message = "L'historique de conversation est trop long")
    private List<AiChatTurnDto> history = new ArrayList<>();

    @Data
    public static class AiChatTurnDto {

        @NotBlank
        @Pattern(regexp = "user|assistant", message = "role doit être user ou assistant")
        private String role;

        @NotBlank
        @Size(max = 8000)
        private String text;
    }
}
