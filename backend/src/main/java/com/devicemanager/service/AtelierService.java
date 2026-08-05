package com.devicemanager.service;

import com.devicemanager.dto.AtelierRequest;
import com.devicemanager.dto.AtelierResponsableDto;
import com.devicemanager.dto.AtelierSummary;
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
import com.devicemanager.repository.MasRepository;
import com.devicemanager.repository.SfmRepository;
import com.devicemanager.repository.UserRepository;
import com.devicemanager.security.Roles;
import com.devicemanager.tenancy.AtelierContext;
import lombok.RequiredArgsConstructor;
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
public class AtelierService {

    private final AtelierRepository atelierRepository;
    private final CasinoRepository casinoRepository;
    private final UserRepository userRepository;
    private final DeviceRepository deviceRepository;
    private final MasRepository masRepository;
    private final SfmRepository sfmRepository;
    private final CommandeRepository commandeRepository;

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
     * Liste les casinos du groupe de l'utilisateur.
     *
     * @param username nom d'utilisateur connecté
     * @return casinos triés par nom
     */
    public List<CasinoSummary> listCasinosForUser(String username) {
        User user = requireUser(username);
        if (user.getGroupe() == null) {
            return List.of();
        }
        return casinoRepository.findByGroupeIdOrderByNomAsc(user.getGroupe().getId()).stream()
                .map(c -> CasinoSummary.builder()
                        .id(c.getId())
                        .nom(c.getNom())
                        .groupeId(c.getGroupe() != null ? c.getGroupe().getId() : null)
                        .groupeNom(c.getGroupe() != null ? c.getGroupe().getNom() : "")
                        .build())
                .toList();
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
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Sélectionnez un atelier (en-tête X-Atelier-Id)");
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
        return toSummary(atelierRepository.saveAndFlush(atelier));
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
        return toSummary(atelierRepository.saveAndFlush(atelier));
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
        if (devices + masses + sfms + commandes > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Impossible de supprimer : des pièces, MAS, SFM ou demandes sont liées à cet atelier.");
        }
        userRepository.clearPreferredAtelier(id);
        if (atelier.getResponsables() != null) {
            atelier.getResponsables().clear();
        }
        atelierRepository.delete(atelier);
        atelierRepository.flush();
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
                .responsables(atelier.getResponsables() == null
                        ? List.of()
                        : atelier.getResponsables().stream()
                                .map(this::toResponsableDto)
                                .sorted((a, b) -> {
                                    String la = ((a.getNom() == null ? "" : a.getNom())
                                            + " " + (a.getPrenom() == null ? "" : a.getPrenom())).trim();
                                    String lb = ((b.getNom() == null ? "" : b.getNom())
                                            + " " + (b.getPrenom() == null ? "" : b.getPrenom())).trim();
                                    return la.compareToIgnoreCase(lb);
                                })
                                .toList())
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
                    if (actor.getGroupe() == null
                            || candidate.getGroupe() == null
                            || !actor.getGroupe().getId().equals(candidate.getGroupe().getId())) {
                        throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                                "Responsable hors du groupe de l'atelier");
                    }
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
