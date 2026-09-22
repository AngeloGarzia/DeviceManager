package com.devicemanager.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * Corps de requête pour {@code PUT /api/todos/recurrences/{id}/active}.
 * <p>Impose la présence explicite du booléen {@code active} et évite un NPE
 * lorsque le body est absent.
 */
@Data
public class TodoRecurrenceToggleRequest {

    /** {@code true} pour activer la récurrence, {@code false} pour la mettre en pause. */
    @NotNull
    private Boolean active;
}
