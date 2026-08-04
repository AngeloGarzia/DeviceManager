package com.devicemanager.dto;

import lombok.Builder;
import lombok.Data;

/**
 * Représentation d'un contact SFM renvoyée par l'API.
 */
@Data
@Builder
public class SfmContactResponse {
    private Long id;
    private String nom;
    private String telephone;
    private String email;
    /** Indique si ce contact reçoit les e-mails de commande validée. */
    private boolean receiveOrderMails;
}
