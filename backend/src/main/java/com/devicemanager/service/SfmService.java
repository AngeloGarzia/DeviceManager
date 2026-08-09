package com.devicemanager.service;

import com.devicemanager.dto.MarqueMasResponse;
import com.devicemanager.dto.SfmContactResponse;
import com.devicemanager.dto.SfmRequest;
import com.devicemanager.dto.SfmResponse;
import com.devicemanager.dto.SfmTechnicienResponse;
import com.devicemanager.entity.Atelier;
import com.devicemanager.entity.MarqueMas;
import com.devicemanager.entity.Sfm;
import com.devicemanager.entity.SfmContact;
import com.devicemanager.repository.MarqueMasRepository;
import com.devicemanager.repository.SfmContactRepository;
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
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Service métier des SFM (fournisseurs de pièces détachées casino).
 * <p>
 * CRUD des fournisseurs avec contacts partageables (dont techniciens multi-SFM)
 * et marques couvertes, filtré par l'atelier courant ({@code X-Atelier-Id}).
 */
@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class SfmService {

    private final SfmRepository sfmRepository;
    private final SfmContactRepository sfmContactRepository;
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

    /**
     * Liste les techniciens SFM déjà utilisés dans l'atelier courant.
     */
    @Transactional(readOnly = true)
    public List<SfmTechnicienResponse> listTechniciens() {
        Long atelierId = atelierService.requireCurrentAtelier().getId();
        return sfmContactRepository.findTechniciensByAtelierId(atelierId).stream()
                .map(this::toTechnicienResponse)
                .toList();
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
        log.info("Création en base — SFM id={} nom={} marques={} atelier={}",
                saved.getId(), saved.getNom(), saved.getMarques().size(), atelier.getId());
        return toResponse(saved);
    }

    public SfmResponse update(Long id, SfmRequest request) {
        Sfm entity = getEntity(id);
        String nom = request.getNom().trim();
        ensureUniqueNom(nom, entity.getAtelier().getId(), entity.getId());
        entity.setNom(nom);
        applyContacts(entity, request.getContacts());
        applyMarques(entity, request.getMarqueIds());
        entity.syncPrimaryContactFields();
        Sfm saved = sfmRepository.save(entity);
        log.info("Modification en base — SFM id={} nom={} marques={} atelier={}",
                saved.getId(),
                saved.getNom(),
                saved.getMarques().size(),
                saved.getAtelier() != null ? saved.getAtelier().getId() : null);
        return toResponse(saved);
    }

    public void delete(Long id) {
        Sfm entity = getEntity(id);
        Long atelierId = entity.getAtelier() != null ? entity.getAtelier().getId() : null;
        String nom = entity.getNom();
        List<SfmContact> linked = new ArrayList<>(entity.getContacts());
        entity.getContacts().clear();
        sfmRepository.delete(entity);
        for (SfmContact contact : linked) {
            deleteContactIfOrphan(contact.getId());
        }
        log.info("Suppression en base — SFM id={} nom={} atelier={}", id, nom, atelierId);
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

        Map<Long, SfmContact> previousById = entity.getContacts().stream()
                .filter(c -> c.getId() != null)
                .collect(Collectors.toMap(SfmContact::getId, c -> c, (a, b) -> a, LinkedHashMap::new));

        List<SfmContact> next = new ArrayList<>();
        Set<Long> keptIds = new HashSet<>();

        for (SfmRequest.SfmContactRequest req : contacts) {
            SfmContact contact = resolveContact(req);
            keptIds.add(contact.getId());
            if (!next.contains(contact)) {
                next.add(contact);
            }
        }

        List<SfmContact> removed = previousById.values().stream()
                .filter(c -> !keptIds.contains(c.getId()))
                .toList();

        entity.getContacts().clear();
        for (SfmContact contact : next) {
            entity.addContact(contact);
        }

        for (SfmContact contact : removed) {
            if (contact.getSfms() != null) {
                contact.getSfms().remove(entity);
            }
            deleteContactIfOrphan(contact.getId());
        }
    }

    private SfmContact resolveContact(SfmRequest.SfmContactRequest req) {
        String nom = req.getNom().trim();
        String telephone = req.getTelephone().trim();
        String email = req.getEmail().trim();
        boolean receiveMails = req.getReceiveOrderMails() == null || Boolean.TRUE.equals(req.getReceiveOrderMails());
        boolean technicien = Boolean.TRUE.equals(req.getTechnicienSfm());

        SfmContact contact = null;
        if (req.getId() != null) {
            contact = sfmContactRepository.findById(req.getId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Contact introuvable"));
        } else if (technicien) {
            contact = sfmContactRepository.findByEmailIgnoreCase(email).orElse(null);
            if (contact != null && !contact.isTechnicienSfm()) {
                // Même e-mail qu'un contact non-technicien : on upgrade en technicien partageable.
                contact.setTechnicienSfm(true);
            }
        }

        if (contact == null) {
            contact = SfmContact.builder()
                    .nom(nom)
                    .telephone(telephone)
                    .email(email)
                    .receiveOrderMails(receiveMails)
                    .technicienSfm(technicien)
                    .sfms(new HashSet<>())
                    .build();
        } else {
            contact.setNom(nom);
            contact.setTelephone(telephone);
            contact.setEmail(email);
            contact.setReceiveOrderMails(receiveMails);
            // Respecter la case à cocher (y compris pour décocher un technicien).
            contact.setTechnicienSfm(technicien);
        }
        return sfmContactRepository.save(contact);
    }

    private void deleteContactIfOrphan(Long contactId) {
        if (contactId == null) {
            return;
        }
        long links = sfmContactRepository.countSfmsByContactId(contactId);
        if (links == 0) {
            sfmContactRepository.findById(contactId).ifPresent(sfmContactRepository::delete);
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

    private SfmTechnicienResponse toTechnicienResponse(SfmContact c) {
        List<Sfm> sfms = c.getSfms() == null ? List.of() : c.getSfms().stream()
                .sorted(Comparator.comparing(Sfm::getNom, String.CASE_INSENSITIVE_ORDER))
                .toList();
        return SfmTechnicienResponse.builder()
                .id(c.getId())
                .nom(c.getNom())
                .telephone(c.getTelephone())
                .email(c.getEmail())
                .receiveOrderMails(c.isReceiveOrderMails())
                .sfmIds(sfms.stream().map(Sfm::getId).filter(Objects::nonNull).toList())
                .sfmNoms(sfms.stream().map(Sfm::getNom).toList())
                .build();
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
                        .technicienSfm(c.isTechnicienSfm())
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
                    .technicienSfm(false)
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
