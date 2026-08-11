package com.devicemanager.service;

import com.devicemanager.dto.AtelierRequest;
import com.devicemanager.dto.AtelierResponsableDto;
import com.devicemanager.dto.AtelierSummary;
import com.devicemanager.dto.CasinoRequest;
import com.devicemanager.dto.CasinoSummary;
import com.devicemanager.dto.coordonnees.AdressePostaleDto;
import com.devicemanager.dto.coordonnees.CoordonneesDto;
import com.devicemanager.dto.coordonnees.EmailCoordDto;
import com.devicemanager.dto.coordonnees.ReseauSocialDto;
import com.devicemanager.dto.coordonnees.TelephoneCoordDto;
import com.devicemanager.entity.Atelier;
import com.devicemanager.entity.Casino;
import com.devicemanager.entity.User;
import com.devicemanager.entity.coordonnees.AdressePostale;
import com.devicemanager.entity.coordonnees.Coordonnees;
import com.devicemanager.entity.coordonnees.EmailCoord;
import com.devicemanager.entity.coordonnees.ReseauSocial;
import com.devicemanager.entity.coordonnees.TelephoneCoord;
import com.devicemanager.entity.coordonnees.TypeReseauSocial;
import com.devicemanager.repository.AtelierRepository;
import com.devicemanager.repository.CasinoRepository;
import com.devicemanager.repository.CommandeRepository;
import com.devicemanager.repository.DeviceRepository;
import com.devicemanager.repository.InterventionRepository;
import com.devicemanager.repository.MasRepository;
import com.devicemanager.repository.SfmRepository;
import com.devicemanager.repository.UserRepository;
import com.devicemanager.security.Roles;
import com.devicemanager.tenancy.AtelierContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.Hibernate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Service de gestion des ateliers techniques et du contexte multi-tenant.
 * <p>
 * Résout les ateliers par groupe casino, gère l'atelier courant via
 * {@link com.devicemanager.tenancy.AtelierContext} ({@code X-Atelier-Id})
 * et administre les coordonnées et responsables d'atelier.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Slf4j
public class AtelierService {

    private final AtelierRepository atelierRepository;
    private final CasinoRepository casinoRepository;
    private final UserRepository userRepository;
    private final DeviceRepository deviceRepository;
    private final MasRepository masRepository;
    private final SfmRepository sfmRepository;
    private final CommandeRepository commandeRepository;
    private final InterventionRepository interventionRepository;

    /**
     * Liste les ateliers accessibles à un utilisateur selon son rôle et son groupe.
     *
     * @param username nom d'utilisateur connecté
     * @return tous les ateliers du groupe (admin) ou l'atelier préféré seul (technicien)
     * @throws org.springframework.web.server.ResponseStatusException {@code 401} si utilisateur introuvable
     */
    public List<AtelierSummary> listForUser(String username) {
        User user = requireUser(username);
        if (user.getGroupe() == null) {
            return List.of();
        }
        // Technicien : uniquement son atelier préféré (pas de bascule libre).
        if (isTechnicien(user.getRole())) {
            Atelier preferred = user.getPreferredAtelier();
            if (preferred == null) {
                return List.of();
            }
            return List.of(toSummary(requireAtelierInUserGroupe(user, preferred.getId())));
        }
        return atelierRepository.findAllByGroupeId(user.getGroupe().getId()).stream()
                .map(this::toSummary)
                .toList();
    }

    /**
     * Résout un atelier du groupe de l'utilisateur (création / affectation compte).
     *
     * @param user utilisateur authentifié
     * @param atelierId identifiant de l'atelier
     * @return entité atelier vérifiée
     * @throws org.springframework.web.server.ResponseStatusException {@code 403} si hors groupe ;
     *         {@code 400} si introuvable
     */
    public Atelier requireAtelierForUserGroupe(User user, Long atelierId) {
        return requireAtelierInUserGroupe(user, atelierId);
    }

    /**
     * Liste les casinos du groupe de l'utilisateur (chaque casino possède 0..N ateliers).
     *
     * @param username nom d'utilisateur connecté
     * @return casinos triés par nom, avec nombre d'ateliers
     */
    public List<CasinoSummary> listCasinosForUser(String username) {
        User user = requireUser(username);
        if (user.getGroupe() == null) {
            return List.of();
        }
        return casinoRepository.findByGroupeIdOrderByNomAsc(user.getGroupe().getId()).stream()
                .map(this::toCasinoSummary)
                .toList();
    }

    /**
     * Crée un casino dans le groupe de l'administrateur.
     *
     * @param username administrateur
     * @param request  nom du casino
     * @return casino créé
     */
    @Transactional
    public CasinoSummary createCasino(String username, CasinoRequest request) {
        User user = requireUser(username);
        if (user.getGroupe() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Votre compte n'est rattaché à aucun groupe. Contactez un administrateur.");
        }
        String nom = normalizeNom(request.getNom());
        Long groupeId = user.getGroupe().getId();
        if (casinoRepository.existsByNomIgnoreCaseAndGroupeId(nom, groupeId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Nom de casino déjà utilisé dans ce groupe");
        }
        Casino saved = casinoRepository.saveAndFlush(Casino.builder()
                .nom(nom)
                .groupe(user.getGroupe())
                .build());
        log.info("Création en base — Casino id={} nom={} groupe={} par={}",
                saved.getId(), saved.getNom(), user.getGroupe().getNom(), username);
        return toCasinoSummary(saved);
    }

    /**
     * Renomme un casino du groupe.
     *
     * @param username administrateur
     * @param id       casino
     * @param request  nouveau nom
     * @return casino modifié
     */
    @Transactional
    public CasinoSummary updateCasino(String username, Long id, CasinoRequest request) {
        User user = requireUser(username);
        Casino casino = requireCasinoInUserGroupe(user, id);
        String nom = normalizeNom(request.getNom());
        Long groupeId = user.getGroupe().getId();
        if (casinoRepository.existsByNomIgnoreCaseAndGroupeIdAndIdNot(nom, groupeId, id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Nom de casino déjà utilisé dans ce groupe");
        }
        casino.setNom(nom);
        Casino saved = casinoRepository.saveAndFlush(casino);
        log.info("Modification en base — Casino id={} nom={} par={}", saved.getId(), saved.getNom(), username);
        return toCasinoSummary(saved);
    }

    /**
     * Supprime un casino sans atelier rattaché.
     *
     * @param username administrateur
     * @param id       casino
     */
    @Transactional
    public void deleteCasino(String username, Long id) {
        User user = requireUser(username);
        Casino casino = requireCasinoInUserGroupe(user, id);
        long ateliers = atelierRepository.countByCasinoId(id);
        if (ateliers > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Impossible de supprimer : ce casino possède encore " + ateliers + " atelier(s).");
        }
        String nom = casino.getNom();
        casinoRepository.delete(casino);
        casinoRepository.flush();
        log.info("Suppression en base — Casino id={} nom={} par={}", id, nom, username);
    }

    /**
     * Liste les utilisateurs du même groupe (candidats responsables d'atelier).
     *
     * @param username administrateur connecté
     * @return utilisateurs du groupe
     */
    public List<AtelierResponsableDto> listUsersForGroupe(String username) {
        User user = requireUser(username);
        if (user.getGroupe() == null) {
            return List.of();
        }
        return userRepository.findAllByGroupeId(user.getGroupe().getId()).stream()
                .map(this::toResponsableDto)
                .toList();
    }

    /**
     * Charge l'atelier courant depuis le contexte thread-local ({@code X-Atelier-Id}).
     *
     * @return entité atelier avec casino
     * @throws org.springframework.web.server.ResponseStatusException {@code 400} si en-tête absent ou atelier introuvable
     */
    public Atelier requireCurrentAtelier() {
        Long id = AtelierContext.get();
        if (id == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Sélectionnez un atelier pour continuer.");
        }
        return atelierRepository.findByIdWithCasino(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Atelier introuvable"));
    }

    /**
     * Enregistre l'atelier préféré d'un administrateur pour les prochaines sessions.
     *
     * @param username administrateur connecté
     * @param atelierId identifiant de l'atelier à mémoriser
     * @return résumé de l'atelier choisi
     * @throws org.springframework.web.server.ResponseStatusException {@code 403} si non admin ou hors groupe
     */
    @Transactional
    public AtelierSummary setPreferredAtelier(String username, Long atelierId) {
        User user = requireUser(username);
        if (!Roles.ADMIN.equals(user.getRole())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Seuls les administrateurs peuvent changer d'atelier");
        }
        Atelier atelier = requireAtelierInUserGroupe(user, atelierId);
        user.setPreferredAtelier(atelier);
        userRepository.saveAndFlush(user);
        return toSummary(atelier);
    }

    /**
     * Crée un atelier rattaché à un casino du groupe de l'administrateur.
     *
     * @param username administrateur connecté
     * @param request nom, casino, coordonnées et responsables
     * @return atelier créé
     * @throws org.springframework.web.server.ResponseStatusException {@code 409} si nom déjà utilisé pour ce casino
     */
    @Transactional
    public AtelierSummary create(String username, AtelierRequest request) {
        User user = requireUser(username);
        Casino casino = requireCasinoInUserGroupe(user, request.getCasinoId());
        String nom = normalizeNom(request.getNom());
        ensureUniqueNom(nom, casino.getId(), null);
        Atelier atelier = Atelier.builder()
                .nom(nom)
                .casino(casino)
                .responsables(new HashSet<>())
                .build();
        applyCoordonnees(atelier, request);
        applyResponsables(atelier, user, request.getResponsableIds());
        Atelier saved = atelierRepository.saveAndFlush(atelier);
        applyUtilisateursPreferes(saved, user, request.getUtilisateurPrefereIds());
        log.info("Création en base — Atelier id={} nom={} casino={} par={}",
                saved.getId(), saved.getNom(), casino.getNom(), username);
        return toSummary(saved);
    }

    /**
     * Met à jour un atelier existant du groupe.
     *
     * @param username administrateur connecté
     * @param id identifiant de l'atelier
     * @param request données mises à jour
     * @return atelier modifié
     * @throws org.springframework.web.server.ResponseStatusException {@code 403} si hors groupe ;
     *         {@code 409} en cas de conflit de nom
     */
    @Transactional
    public AtelierSummary update(String username, Long id, AtelierRequest request) {
        User user = requireUser(username);
        Atelier atelier = requireAtelierInUserGroupe(user, id);
        Casino casino = requireCasinoInUserGroupe(user, request.getCasinoId());
        String nom = normalizeNom(request.getNom());
        ensureUniqueNom(nom, casino.getId(), id);
        atelier.setNom(nom);
        atelier.setCasino(casino);
        applyCoordonnees(atelier, request);
        applyResponsables(atelier, user, request.getResponsableIds());
        Atelier saved = atelierRepository.saveAndFlush(atelier);
        applyUtilisateursPreferes(saved, user, request.getUtilisateurPrefereIds());
        log.info("Modification en base — Atelier id={} nom={} casino={} par={}",
                saved.getId(), saved.getNom(), casino.getNom(), username);
        return toSummary(saved);
    }

    /**
     * Supprime un atelier sans données métier rattachées.
     *
     * @param username administrateur connecté
     * @param id identifiant de l'atelier
     * @throws org.springframework.web.server.ResponseStatusException {@code 409} si pièces, MAS, SFM ou commandes liées
     */
    @Transactional
    public void delete(String username, Long id) {
        User user = requireUser(username);
        Atelier atelier = requireAtelierInUserGroupe(user, id);
        long devices = deviceRepository.countByAtelierId(id);
        long masses = masRepository.countByAtelierId(id);
        long sfms = sfmRepository.countByAtelierId(id);
        long commandes = commandeRepository.countByAtelierId(id);
        long interventions = interventionRepository.countByAtelierId(id);
        if (devices + masses + sfms + commandes + interventions > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Impossible de supprimer : des pièces, MAS, SFM, demandes ou bons d'intervention sont liés à cet atelier.");
        }
        String nom = atelier.getNom();

        // UPDATE JPQL + clear du contexte : requireUser JOIN FETCH preferredAtelier laisse sinon
        // une référence gérée vers l'atelier → TransientObjectException au flush delete.
        userRepository.clearPreferredAtelier(id);

        Atelier toDelete = atelierRepository.findByIdWithCasino(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Atelier introuvable"));
        Hibernate.initialize(toDelete.getResponsables());
        if (toDelete.getResponsables() != null) {
            toDelete.getResponsables().clear();
        }
        atelierRepository.delete(toDelete);
        atelierRepository.flush();
        log.info("Suppression en base — Atelier id={} nom={} par={}", id, nom, username);
    }

    /**
     * Convertit une entité atelier en DTO résumé pour l'API.
     *
     * @param atelier entité persistée
     * @return résumé avec casino, groupe, coordonnées et responsables
     */
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
                .coordonnees(toCoordonneesDto(atelier.getCoordonnees()))
                .responsables(sortedUserDtos(atelier.getResponsables() == null
                        ? List.of()
                        : atelier.getResponsables().stream().map(this::toResponsableDto).toList()))
                .utilisateursPreferes(atelier.getId() == null
                        ? List.of()
                        : sortedUserDtos(userRepository.findAllByPreferredAtelierId(atelier.getId()).stream()
                                .map(this::toResponsableDto)
                                .toList()))
                .build();
    }

    private List<AtelierResponsableDto> sortedUserDtos(List<AtelierResponsableDto> users) {
        return users.stream()
                .sorted((a, b) -> {
                    String la = ((a.getNom() == null ? "" : a.getNom())
                            + " " + (a.getPrenom() == null ? "" : a.getPrenom())).trim();
                    String lb = ((b.getNom() == null ? "" : b.getNom())
                            + " " + (b.getPrenom() == null ? "" : b.getPrenom())).trim();
                    if (la.isBlank()) {
                        la = a.getUsername() == null ? "" : a.getUsername();
                    }
                    if (lb.isBlank()) {
                        lb = b.getUsername() == null ? "" : b.getUsername();
                    }
                    return la.compareToIgnoreCase(lb);
                })
                .toList();
    }

    private CasinoSummary toCasinoSummary(Casino casino) {
        long atelierCount = casino.getId() == null ? 0L : atelierRepository.countByCasinoId(casino.getId());
        return CasinoSummary.builder()
                .id(casino.getId())
                .nom(casino.getNom())
                .groupeId(casino.getGroupe() != null ? casino.getGroupe().getId() : null)
                .groupeNom(casino.getGroupe() != null ? casino.getGroupe().getNom() : "")
                .atelierCount(atelierCount)
                .build();
    }

    private void applyCoordonnees(Atelier atelier, AtelierRequest request) {
        Coordonnees coord = atelier.getCoordonnees();
        if (coord == null) {
            coord = Coordonnees.builder()
                    .adresse(new AdressePostale())
                    .emails(new ArrayList<>())
                    .telephones(new ArrayList<>())
                    .reseauxSociaux(new ArrayList<>())
                    .build();
            atelier.setCoordonnees(coord);
        }

        AdressePostaleDto adresseDto = request.getAdresse();
        AdressePostale adresse = coord.getAdresse() != null ? coord.getAdresse() : new AdressePostale();
        if (adresseDto != null) {
            adresse.setLigne1(trimToNull(adresseDto.getLigne1()));
            adresse.setLigne2(trimToNull(adresseDto.getLigne2()));
            adresse.setCodePostal(trimToNull(adresseDto.getCodePostal()));
            adresse.setVille(trimToNull(adresseDto.getVille()));
            adresse.setPays(trimToNull(adresseDto.getPays()));
        }
        coord.setAdresse(adresse);

        coord.clearEmails();
        List<EmailCoordDto> emails = request.getEmails() == null ? List.of() : request.getEmails();
        boolean anyPrincipalEmail = emails.stream().anyMatch(e -> e != null && e.isPrincipal() && hasText(e.getValeur()));
        int emailIndex = 0;
        for (EmailCoordDto dto : emails) {
            if (dto == null || !hasText(dto.getValeur())) {
                continue;
            }
            boolean principal = dto.isPrincipal() || (!anyPrincipalEmail && emailIndex == 0);
            coord.addEmail(EmailCoord.builder()
                    .valeur(dto.getValeur().trim())
                    .principal(principal)
                    .build());
            emailIndex++;
        }

        coord.clearTelephones();
        List<TelephoneCoordDto> telephones = request.getTelephones() == null ? List.of() : request.getTelephones();
        boolean anyPrincipalTel = telephones.stream().anyMatch(t -> t != null && t.isPrincipal() && hasText(t.getValeur()));
        int telIndex = 0;
        for (TelephoneCoordDto dto : telephones) {
            if (dto == null || !hasText(dto.getValeur())) {
                continue;
            }
            boolean principal = dto.isPrincipal() || (!anyPrincipalTel && telIndex == 0);
            coord.addTelephone(TelephoneCoord.builder()
                    .valeur(dto.getValeur().trim())
                    .label(trimToNull(dto.getLabel()))
                    .principal(principal)
                    .build());
            telIndex++;
        }

        coord.clearReseauxSociaux();
        List<ReseauSocialDto> reseaux = request.getReseauxSociaux() == null ? List.of() : request.getReseauxSociaux();
        for (ReseauSocialDto dto : reseaux) {
            if (dto == null || !hasText(dto.getUrl())) {
                continue;
            }
            TypeReseauSocial type = dto.getType() != null ? dto.getType() : TypeReseauSocial.AUTRE;
            coord.addReseauSocial(ReseauSocial.builder()
                    .type(type)
                    .url(dto.getUrl().trim())
                    .build());
        }
    }

    private void applyResponsables(Atelier atelier, User actor, List<Long> responsableIds) {
        Set<User> next = new HashSet<>();
        if (responsableIds != null && !responsableIds.isEmpty()) {
            List<Long> ids = responsableIds.stream().filter(Objects::nonNull).distinct().toList();
            if (!ids.isEmpty()) {
                List<User> users = userRepository.findAllByIdInWithGroupe(ids);
                if (users.size() != ids.size()) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Responsable introuvable");
                }
                for (User candidate : users) {
                    ensureSameGroupe(actor, candidate, "Responsable hors du groupe de l'atelier");
                    next.add(candidate);
                }
            }
        }
        if (atelier.getResponsables() == null) {
            atelier.setResponsables(next);
        } else {
            atelier.getResponsables().clear();
            atelier.getResponsables().addAll(next);
        }
    }

    /**
     * Définit les utilisateurs ayant cet atelier comme préféré.
     * Les utilisateurs retirés voient leur atelier préféré remis à null ;
     * ceux ajoutés pointent vers cet atelier (un seul préféré par compte).
     */
    private void applyUtilisateursPreferes(Atelier atelier, User actor, List<Long> utilisateurPrefereIds) {
        if (atelier.getId() == null) {
            return;
        }
        List<Long> desiredIds = utilisateurPrefereIds == null
                ? List.of()
                : utilisateurPrefereIds.stream().filter(Objects::nonNull).distinct().toList();
        Set<Long> desired = new HashSet<>(desiredIds);

        List<User> currentlyPreferring = userRepository.findAllByPreferredAtelierId(atelier.getId());
        List<User> toClear = currentlyPreferring.stream()
                .filter(current -> !desired.contains(current.getId()))
                .peek(current -> current.setPreferredAtelier(null))
                .toList();
        if (!toClear.isEmpty()) {
            userRepository.saveAll(toClear);
        }

        if (desiredIds.isEmpty()) {
            return;
        }
        List<User> candidates = userRepository.findAllByIdInWithGroupe(desiredIds);
        if (candidates.size() != desiredIds.size()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Utilisateur préféré introuvable");
        }
        for (User candidate : candidates) {
            ensureSameGroupe(actor, candidate, "Utilisateur préféré hors du groupe de l'atelier");
            candidate.setPreferredAtelier(atelier);
        }
        userRepository.saveAll(candidates);
    }

    private void ensureSameGroupe(User actor, User candidate, String forbiddenMessage) {
        if (actor.getGroupe() == null
                || candidate.getGroupe() == null
                || !actor.getGroupe().getId().equals(candidate.getGroupe().getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, forbiddenMessage);
        }
    }

    private CoordonneesDto toCoordonneesDto(Coordonnees coord) {
        if (coord == null) {
            return CoordonneesDto.builder()
                    .adresse(AdressePostaleDto.builder().build())
                    .emails(List.of())
                    .telephones(List.of())
                    .reseauxSociaux(List.of())
                    .build();
        }
        AdressePostale adresse = coord.getAdresse();
        return CoordonneesDto.builder()
                .id(coord.getId())
                .adresse(AdressePostaleDto.builder()
                        .ligne1(adresse != null ? adresse.getLigne1() : null)
                        .ligne2(adresse != null ? adresse.getLigne2() : null)
                        .codePostal(adresse != null ? adresse.getCodePostal() : null)
                        .ville(adresse != null ? adresse.getVille() : null)
                        .pays(adresse != null ? adresse.getPays() : null)
                        .build())
                .emails(coord.getEmails() == null ? List.of() : coord.getEmails().stream()
                        .map(e -> {
                            EmailCoordDto dto = new EmailCoordDto();
                            dto.setId(e.getId());
                            dto.setValeur(e.getValeur());
                            dto.setPrincipal(e.isPrincipal());
                            return dto;
                        })
                        .collect(Collectors.toCollection(ArrayList::new)))
                .telephones(coord.getTelephones() == null ? List.of() : coord.getTelephones().stream()
                        .map(t -> {
                            TelephoneCoordDto dto = new TelephoneCoordDto();
                            dto.setId(t.getId());
                            dto.setValeur(t.getValeur());
                            dto.setLabel(t.getLabel());
                            dto.setPrincipal(t.isPrincipal());
                            return dto;
                        })
                        .collect(Collectors.toCollection(ArrayList::new)))
                .reseauxSociaux(coord.getReseauxSociaux() == null ? List.of() : coord.getReseauxSociaux().stream()
                        .map(r -> {
                            ReseauSocialDto dto = new ReseauSocialDto();
                            dto.setId(r.getId());
                            dto.setType(r.getType());
                            dto.setUrl(r.getUrl());
                            return dto;
                        })
                        .collect(Collectors.toCollection(ArrayList::new)))
                .build();
    }

    private AtelierResponsableDto toResponsableDto(User user) {
        return AtelierResponsableDto.builder()
                .id(user.getId())
                .username(user.getUsername())
                .nom(user.getNom())
                .prenom(user.getPrenom())
                .email(user.getEmail())
                .build();
    }

    private Atelier requireAtelierInUserGroupe(User user, Long atelierId) {
        Atelier atelier = atelierRepository.findByIdWithCasino(atelierId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Atelier introuvable"));
        assertSameGroupe(user, atelier.getCasino());
        return atelier;
    }

    private Casino requireCasinoInUserGroupe(User user, Long casinoId) {
        Casino casino = casinoRepository.findByIdWithGroupe(casinoId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Casino introuvable"));
        assertSameGroupe(user, casino);
        return casino;
    }

    private void assertSameGroupe(User user, Casino casino) {
        if (user.getGroupe() == null
                || casino == null
                || casino.getGroupe() == null
                || !user.getGroupe().getId().equals(casino.getGroupe().getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Atelier non autorisé pour ce compte");
        }
    }

    private void ensureUniqueNom(String nom, Long casinoId, Long excludeId) {
        boolean exists = excludeId == null
                ? atelierRepository.findByNomIgnoreCaseAndCasinoId(nom, casinoId).isPresent()
                : atelierRepository.findByNomIgnoreCaseAndCasinoId(nom, casinoId)
                        .filter(a -> !a.getId().equals(excludeId))
                        .isPresent();
        if (exists) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Un atelier avec ce nom existe déjà pour ce casino");
        }
    }

    private static String normalizeNom(String nom) {
        if (nom == null || nom.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Le nom de l'atelier est obligatoire");
        }
        return nom.trim();
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private User requireUser(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Utilisateur introuvable"));
    }

    private static boolean isTechnicien(String role) {
        return Roles.TECHNICIEN.equals(role) || "TECH".equals(role);
    }
}
