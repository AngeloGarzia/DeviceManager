package com.devicemanager.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * Tableau de bord nettoyage MAS : listes + pourcentages atelier.
 */
@Data
@Builder
public class TodoNettoyageAnalyseResponse {

    /** Jours au-delà desquels la fraîcheur tombe à 0 (rouge écarlate). */
    private int seuilJoursCritique;

    private int totalMas;
    private int jamaisNettoyees;
    private int nettoyees7j;
    private int nettoyees30j;
    private int nettoyees90j;

    private double pctJamais;
    private double pct7j;
    private double pct30j;
    private double pct90j;

    /** Moyenne des jours depuis dernier nettoyage (MAS déjà nettoyées uniquement). */
    private Double moyenneJoursDepuis;

    private int bucketRecent;
    private int bucketOk;
    private int bucketAttention;
    private int bucketUrgent;
    private int bucketCritique;
    private int bucketJamais;

    private List<TodoNettoyageMasStatut> items;
}
