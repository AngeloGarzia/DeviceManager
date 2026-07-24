package com.devicemanager.service;

import com.devicemanager.dto.AtelierSummary;
import com.devicemanager.entity.Atelier;
import com.devicemanager.entity.User;
import com.devicemanager.repository.AtelierRepository;
import com.devicemanager.repository.UserRepository;
import com.devicemanager.tenancy.AtelierContext;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AtelierService {

    private final AtelierRepository atelierRepository;
    private final UserRepository userRepository;

    public List<AtelierSummary> listForUser(String username) {
        User user = requireUser(username);
        if (user.getGroupe() == null) {
            return List.of();
        }
        return atelierRepository.findAllByGroupeId(user.getGroupe().getId()).stream()
                .map(this::toSummary)
                .toList();
    }

    public Atelier requireCurrentAtelier() {
        Long id = AtelierContext.get();
        if (id == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Sélectionnez un atelier (en-tête X-Atelier-Id)");
        }
        return atelierRepository.findByIdWithCasino(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Atelier introuvable"));
    }

    public AtelierSummary toSummary(Atelier atelier) {
        String casinoNom = atelier.getCasino() != null ? atelier.getCasino().getNom() : "";
        String groupeNom = atelier.getCasino() != null && atelier.getCasino().getGroupe() != null
                ? atelier.getCasino().getGroupe().getNom()
                : "";
        return AtelierSummary.builder()
                .id(atelier.getId())
                .nom(atelier.getNom())
                .casinoId(atelier.getCasino() != null ? atelier.getCasino().getId() : null)
                .casinoNom(casinoNom)
                .groupeId(atelier.getCasino() != null && atelier.getCasino().getGroupe() != null
                        ? atelier.getCasino().getGroupe().getId() : null)
                .groupeNom(groupeNom)
                .label(atelier.getNom() + " — " + casinoNom)
                .build();
    }

    private User requireUser(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Utilisateur introuvable"));
    }
}
