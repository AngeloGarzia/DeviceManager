package com.devicemanager.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Data
@Builder
public class TodoRecurrenceResponse {

    private Long id;
    private String titre;
    private String description;
    private String severite;
    private Long masId;
    private String masNumero;
    private String frequence;
    private Integer intervalDays;
    private Integer jourSemaine;
    private Integer jourMois;
    private LocalTime heureDue;
    private LocalDate dateDebut;
    private LocalDate dateFin;
    private boolean active;
    private LocalDateTime prochaineEcheance;
    private String createdByUsername;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
