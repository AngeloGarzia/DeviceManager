package com.devicemanager.service;

import com.devicemanager.dto.MarqueMasResponse;
import com.devicemanager.dto.SfmContactResponse;
import com.devicemanager.dto.SfmRequest;
import com.devicemanager.dto.SfmResponse;
import com.devicemanager.entity.Atelier;
import com.devicemanager.entity.MarqueMas;
import com.devicemanager.entity.Sfm;
import com.devicemanager.entity.SfmContact;
import com.devicemanager.repository.MarqueMasRepository;
import com.devicemanager.repository.SfmRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.Hibernate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class SfmService {

    private final SfmRepository sfmRepository;
    private final MarqueMasRepository marqueMasRepository;
    private final AtelierService atelierService;

    @Transactional(readOnly = true)
    public List<SfmResponse> findAll(String q) {
        Long atelierId = atelierService.requireCurrentAtelier().getId();
        List<Sfm> list = (q == null || q.isBlank())
                ? sfmRepository.findAllWithContacts(atelierId)
                : sfmRepository.search(atelierId, q.trim());
        list.forEach(s -> Hibernate.initialize(s.getMarques()));
        return list.stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public SfmResponse findById(Long id) {
        return toResponse(getEntity(id));
    }

    public SfmResponse create(SfmRequest request) {
        Atelier atelier = atelierService.requireCurrentAtelier();
        String nom = request.getNom().trim();
        ensureUniqueNom(nom, atelier.getId(), null);
        Sfm entity = Sfm.builder()
                .nom(nom)
                .responsable("")
                .telephone("")
                .email("")
                .contacts(new ArrayList<>())
                .marques(new HashSet<>())
                .atelier(atelier)
                .build();
        applyContacts(entity, request.getContacts());
        applyMarques(entity, request.getMarqueIds());
        entity.syncPrimaryContactFields();
        Sfm saved = sfmRepository.save(entity);
        log.info("SFM créé: {} ({} marque(s), atelier={})",
                saved.getNom(), saved.getMarques().size(), atelier.getId());
        return toResponse(saved);
    }

    public SfmResponse update(Long id, SfmRequest request) {
        Sfm entity = getEntity(id);
        String nom = request.getNom().trim();
        ensureUniqueNom(nom, entity.getAtelier().getId(), entity.getId());
        entity.setNom(nom);
        entity.getContacts().clear();
        applyContacts(entity, request.getContacts());
        applyMarques(entity, request.getMarqueIds());
        entity.syncPrimaryContactFields();
        return toResponse(sfmRepository.save(entity));
    }

    public void delete(Long id) {
        Sfm entity = getEntity(id);
        sfmRepository.delete(entity);
    }

    public Sfm getEntity(Long id) {
        Long atelierId = atelierService.requireCurrentAtelier().getId();
        Sfm entity = sfmRepository.findByIdWithContacts(id, atelierId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "SFM introuvable"));
        Hibernate.initialize(entity.getMarques());
        return entity;
    }

    private void ensureUniqueNom(String nom, Long atelierId, Long excludeId) {
        boolean exists = excludeId == null
                ? sfmRepository.existsByNomIgnoreCaseAndAtelierId(nom, atelierId)
                : sfmRepository.existsByNomIgnoreCaseAndAtelierIdAndIdNot(nom, atelierId, excludeId);
        if (exists) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Nom SFM déjà utilisé dans cet atelier");
        }
    }

    private void applyContacts(Sfm entity, List<SfmRequest.SfmContactRequest> contacts) {
        if (contacts == null || contacts.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ajoutez au moins un contact SFM");
        }
        for (SfmRequest.SfmContactRequest c : contacts) {
            boolean receiveMails = c.getReceiveOrderMails() == null || Boolean.TRUE.equals(c.getReceiveOrderMails());
            entity.addContact(SfmContact.builder()
                    .nom(c.getNom().trim())
                    .telephone(c.getTelephone().trim())
                    .email(c.getEmail().trim())
                    .receiveOrderMails(receiveMails)
                    .build());
        }
    }

    private void applyMarques(Sfm entity, List<Long> marqueIds) {
        if (marqueIds == null || marqueIds.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Sélectionnez au moins une marque");
        }
        Set<Long> uniqueIds = new LinkedHashSet<>(marqueIds);
        List<MarqueMas> marques = marqueMasRepository.findAllById(uniqueIds);
        if (marques.size() != uniqueIds.size()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Une ou plusieurs marques sont introuvables");
        }
        entity.getMarques().clear();
        entity.getMarques().addAll(marques);
    }

    private SfmResponse toResponse(Sfm entity) {
        List<SfmContactResponse> contacts = entity.getContacts() == null
                ? List.of()
                : entity.getContacts().stream()
                .map(c -> SfmContactResponse.builder()
                        .id(c.getId())
                        .nom(c.getNom())
                        .telephone(c.getTelephone())
                        .email(c.getEmail())
                        .receiveOrderMails(c.isReceiveOrderMails())
                        .build())
                .toList();

        if (contacts.isEmpty()
                && entity.getResponsable() != null
                && !entity.getResponsable().isBlank()) {
            contacts = List.of(SfmContactResponse.builder()
                    .nom(entity.getResponsable())
                    .telephone(entity.getTelephone())
                    .email(entity.getEmail())
                    .receiveOrderMails(true)
                    .build());
        }

        List<MarqueMasResponse> marques = entity.getMarques() == null
                ? List.of()
                : entity.getMarques().stream()
                .sorted(Comparator.comparing(MarqueMas::getLabel, String.CASE_INSENSITIVE_ORDER))
                .map(m -> MarqueMasResponse.builder()
                        .id(m.getId())
                        .code(m.getCode())
                        .label(m.getLabel())
                        .value(m.getId())
                        .build())
                .toList();

        return SfmResponse.builder()
                .id(entity.getId())
                .nom(entity.getNom())
                .responsable(entity.getResponsable())
                .telephone(entity.getTelephone())
                .email(entity.getEmail())
                .contacts(contacts)
                .marqueIds(marques.stream().map(MarqueMasResponse::getId).collect(Collectors.toList()))
                .marques(marques)
                .build();
    }
}
