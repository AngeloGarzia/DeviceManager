package com.devicemanager.service;

import com.devicemanager.dto.MailPreviewItem;
import com.devicemanager.dto.OrderRequestDto;
import com.devicemanager.dto.OrderRequestLineResponse;
import com.devicemanager.dto.OrderRequestResponse;
import com.devicemanager.entity.Atelier;
import com.devicemanager.entity.Commande;
import com.devicemanager.entity.CommandeLigne;
import com.devicemanager.entity.Device;
import com.devicemanager.entity.Sfm;
import com.devicemanager.entity.SfmContact;
import com.devicemanager.entity.User;
import com.devicemanager.repository.CommandeRepository;
import com.devicemanager.repository.DeviceRepository;
import com.devicemanager.repository.UserRepository;
import com.devicemanager.security.OrderStatuses;
import com.devicemanager.security.Roles;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.Hibernate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class OrderRequestService {

    private final CommandeRepository commandeRepository;
    private final DeviceRepository deviceRepository;
    private final UserRepository userRepository;
    private final MailService mailService;
    private final AtelierService atelierService;

    public OrderRequestResponse create(OrderRequestDto request, String username) {
        User technicien = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Utilisateur introuvable"));
        Atelier atelier = atelierService.requireCurrentAtelier();
        Long atelierId = atelier.getId();

        if (request.getLignes() == null || request.getLignes().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ajoutez au moins une pièce à la demande");
        }

        Map<Long, Integer> quantities = new LinkedHashMap<>();
        for (OrderRequestDto.OrderRequestLineDto line : request.getLignes()) {
            quantities.merge(line.getDeviceId(), line.getQuantite(), Integer::sum);
        }

        LocalDateTime dateDemande = LocalDateTime.now();
        Commande commande = Commande.builder()
                .technicien(technicien)
                .technicienNom(displayUserName(technicien))
                .message(request.getMessage().trim())
                .dateDemande(dateDemande)
                .status(OrderStatuses.PENDING)
                .lignes(new ArrayList<>())
                .atelier(atelier)
                .build();

        List<Device> devices = new ArrayList<>();
        for (Map.Entry<Long, Integer> entry : quantities.entrySet()) {
            Device device = deviceRepository.findByIdWithRelations(entry.getKey(), atelierId)
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.BAD_REQUEST, "Pièce détachée introuvable: " + entry.getKey()));
            CommandeLigne ligne = CommandeLigne.builder()
                    .device(device)
                    .quantite(entry.getValue())
                    .build();
            commande.addLigne(ligne);
            devices.add(device);
        }

        Commande saved = commandeRepository.save(commande);

        try {
            mailService.sendOrderRequestToAdmin(
                    "Demande de commande #" + saved.getId() + " — " + quantities.size() + " pièce(s)",
                    buildAdminNotificationBody(saved, atelier, quantities, devices));
        } catch (Exception ex) {
            log.error("Demande #{} enregistrée mais e-mail admin non envoyé: {}", saved.getId(), ex.getMessage());
        }
        log.info("Demande de commande #{} créée par {} ({} pièce(s))",
                saved.getId(), technicien.getUsername(), quantities.size());
        return toResponse(saved);
    }

    /**
     * Validation admin : passe la demande en VALIDATED et envoie un e-mail par SFM concerné.
     */
    public OrderRequestResponse validate(Long id, String adminUsername) {
        Long atelierId = atelierService.requireCurrentAtelier().getId();
        Commande commande = commandeRepository.findByIdWithRelations(id, atelierId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Demande introuvable"));

        if (!OrderStatuses.isPending(commande.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Cette demande est déjà validée (statut=" + commande.getStatus() + ")");
        }

        User admin = userRepository.findByUsername(adminUsername)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Administrateur introuvable"));

        Map<Long, List<CommandeLigne>> bySfm = groupLinesBySfm(commande);
        int mailsSent = 0;
        List<String> warnings = new ArrayList<>();

        for (Map.Entry<Long, List<CommandeLigne>> entry : bySfm.entrySet()) {
            if (entry.getKey() == null) {
                warnings.add(entry.getValue().size() + " pièce(s) sans SFM — aucun e-mail fournisseur");
                continue;
            }
            Sfm sfm = entry.getValue().getFirst().getDevice().getSfm();
            Hibernate.initialize(sfm.getContacts());
            Set<String> recipients = resolveSfmRecipients(sfm);
            if (recipients.isEmpty()) {
                warnings.add("SFM « " + sfm.getNom() + " » sans e-mail — non notifié");
                continue;
            }
            String subject = buildSfmOrderMailSubject(commande);
            String body = buildSfmOrderMailBody(commande, entry.getValue(), admin);
            for (String to : recipients) {
                try {
                    mailService.send(to, subject, body);
                    mailsSent++;
                } catch (Exception ex) {
                    log.error("Échec e-mail SFM {} ({}): {}", sfm.getNom(), to, ex.getMessage());
                    warnings.add("Échec envoi à " + to + " (" + sfm.getNom() + ")");
                }
            }
        }

        commande.setStatus(OrderStatuses.VALIDATED);
        Commande saved = commandeRepository.save(commande);
        log.info("Demande #{} validée par {} — {} e-mail(s) SFM", id, adminUsername, mailsSent);
        if (!warnings.isEmpty()) {
            log.warn("Demande #{} validation — alertes: {}", id, String.join(" ; ", warnings));
        }
        return toResponse(saved);
    }

    public void delete(Long id, String adminUsername) {
        Long atelierId = atelierService.requireCurrentAtelier().getId();
        Commande commande = commandeRepository.findByIdWithRelations(id, atelierId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Demande introuvable"));
        commandeRepository.delete(commande);
        log.info("Demande #{} supprimée par {}", id, adminUsername);
    }

    @Transactional(readOnly = true)
    public List<OrderRequestResponse> findAll() {
        Long atelierId = atelierService.requireCurrentAtelier().getId();
        return commandeRepository.findAllWithRelationsOrderByDateDesc(atelierId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public long countPending() {
        Long atelierId = atelierService.requireCurrentAtelier().getId();
        return commandeRepository.countByAtelierIdAndStatusIn(
                atelierId, List.of(OrderStatuses.PENDING, OrderStatuses.SENT));
    }

    /**
     * Aperçu de l'e-mail admin qui sera envoyé à la création (sans enregistrer).
     */
    @Transactional(readOnly = true)
    public List<MailPreviewItem> previewCreateMails(OrderRequestDto request, String username) {
        User technicien = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Utilisateur introuvable"));
        Atelier atelier = atelierService.requireCurrentAtelier();
        Long atelierId = atelier.getId();

        if (request.getLignes() == null || request.getLignes().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ajoutez au moins une pièce à la demande");
        }

        Map<Long, Integer> quantities = new LinkedHashMap<>();
        for (OrderRequestDto.OrderRequestLineDto line : request.getLignes()) {
            quantities.merge(line.getDeviceId(), line.getQuantite(), Integer::sum);
        }

        List<Device> devices = new ArrayList<>();
        for (Long deviceId : quantities.keySet()) {
            Device device = deviceRepository.findByIdWithRelations(deviceId, atelierId)
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.BAD_REQUEST, "Pièce détachée introuvable: " + deviceId));
            devices.add(device);
        }

        String message = request.getMessage() == null || request.getMessage().isBlank()
                ? "(message à compléter)"
                : request.getMessage().trim();
        Commande draft = Commande.builder()
                .technicien(technicien)
                .technicienNom(displayUserName(technicien))
                .message(message)
                .dateDemande(LocalDateTime.now())
                .status(OrderStatuses.PENDING)
                .atelier(atelier)
                .lignes(new ArrayList<>())
                .build();
        for (Device device : devices) {
            draft.addLigne(CommandeLigne.builder()
                    .device(device)
                    .quantite(quantities.getOrDefault(device.getId(), 1))
                    .build());
        }

        List<MailPreviewItem> previews = new ArrayList<>();
        String adminSubject = "Demande de commande (aperçu) — " + quantities.size() + " pièce(s)";
        String adminBody = buildAdminNotificationBody(draft, atelier, quantities, devices)
                .replace("Demande n°null", "Demande n°(sera attribuée à l'envoi)");
        previews.add(MailPreviewItem.builder()
                .kind("ADMIN")
                .to(mailService.getAdminEmail())
                .subject(adminSubject)
                .body(adminBody)
                .sfmNom(null)
                .build());
        // Aperçu SFM : si l'auteur est admin, on montre sa signature ; sinon placeholders admin
        User validatorPreview = Roles.ADMIN.equalsIgnoreCase(technicien.getRole()) ? technicien : null;
        previews.addAll(buildSfmMailPreviews(draft, validatorPreview));
        return previews;
    }

    /**
     * Aperçu des e-mails SFM qui seront envoyés à la validation.
     * {@code viewerUsername} = admin connecté (signature affichée dans l'aperçu).
     */
    @Transactional(readOnly = true)
    public List<MailPreviewItem> previewSfmMails(Long id, String viewerUsername) {
        Long atelierId = atelierService.requireCurrentAtelier().getId();
        Commande commande = commandeRepository.findByIdWithRelations(id, atelierId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Demande introuvable"));
        User viewer = userRepository.findByUsername(viewerUsername).orElse(null);
        return buildSfmMailPreviews(commande, viewer);
    }

    private List<MailPreviewItem> buildSfmMailPreviews(Commande commande, User validator) {
        List<MailPreviewItem> previews = new ArrayList<>();
        Map<Long, List<CommandeLigne>> bySfm = groupLinesBySfm(commande);
        for (Map.Entry<Long, List<CommandeLigne>> entry : bySfm.entrySet()) {
            if (entry.getKey() == null) {
                previews.add(MailPreviewItem.builder()
                        .kind("WARNING")
                        .to("—")
                        .subject("Pièces sans SFM")
                        .body(entry.getValue().size()
                                + " pièce(s) sans SFM : aucun e-mail fournisseur ne sera envoyé pour ces lignes.")
                        .sfmNom(null)
                        .build());
                continue;
            }
            Sfm sfm = entry.getValue().getFirst().getDevice().getSfm();
            Hibernate.initialize(sfm.getContacts());
            Set<String> recipients = resolveSfmRecipients(sfm);
            String subject = buildSfmOrderMailSubject(commande);
            String body = buildSfmOrderMailBody(commande, entry.getValue(), validator);
            if (recipients.isEmpty()) {
                previews.add(MailPreviewItem.builder()
                        .kind("WARNING")
                        .to("—")
                        .subject(subject)
                        .body("SFM « " + sfm.getNom() + " » sans adresse e-mail — non notifié.\n\n" + body)
                        .sfmNom(sfm.getNom())
                        .build());
                continue;
            }
            for (String to : recipients) {
                previews.add(MailPreviewItem.builder()
                        .kind("SFM")
                        .to(to)
                        .subject(subject)
                        .body(body)
                        .sfmNom(sfm.getNom())
                        .build());
            }
        }
        return previews;
    }

    private String buildAdminNotificationBody(
            Commande commande,
            Atelier atelier,
            Map<Long, Integer> quantities,
            List<Device> devices) {
        StringBuilder linesBody = new StringBuilder();
        Set<String> sfmLabels = new LinkedHashSet<>();
        for (Device device : devices) {
            int qty = quantities.getOrDefault(device.getId(), 1);
            linesBody.append("- ").append(device.getNom());
            if (device.getReference() != null && !device.getReference().isBlank()) {
                linesBody.append(" (réf. ").append(device.getReference()).append(")");
            }
            linesBody.append(" × ").append(qty);
            if (device.getSfm() != null) {
                linesBody.append(" — SFM: ").append(device.getSfm().getNom());
                sfmLabels.add(device.getSfm().getNom() + " <" + nullToEmpty(device.getSfm().getEmail()) + ">");
            } else {
                linesBody.append(" — SFM: (non renseigné)");
            }
            linesBody.append('\n');
        }

        String sfmBlock = sfmLabels.isEmpty()
                ? "(aucun SFM associé aux pièces)"
                : sfmLabels.stream().map(s -> "- " + s).collect(Collectors.joining("\n"));

        return """
                Bonjour Administrateur,

                Une nouvelle demande de commande nécessite votre validation dans DeviceManager.

                Demande n°%s
                Atelier : %s
                Technicien : %s
                Date/heure : %s

                Pièces détachées :
                %s
                SFM concernés :
                %s

                Message du technicien :
                %s

                Connectez-vous à l'application puis validez la demande pour envoyer la commande aux SFM.

                — DeviceManager
                """.formatted(
                commande.getId(),
                atelier.getNom(),
                commande.getTechnicienNom(),
                commande.getDateDemande(),
                linesBody,
                sfmBlock,
                commande.getMessage()
        );
    }

    private String buildSfmOrderMailSubject(Commande commande) {
        if (commande.getId() == null) {
            return "Demande de devis #(n° à l'envoi)";
        }
        return "Demande de devis #" + commande.getId();
    }

    /**
     * Corps e-mail SFM — signature :
     * <pre>
     * Merci, bien à vous.
     * {Prénom Nom de l'admin validant}
     * {e-mail admin}
     * {e-mail demandeur}
     * </pre>
     */
    private String buildSfmOrderMailBody(Commande commande, List<CommandeLigne> lignes, User validator) {
        StringBuilder linesBody = new StringBuilder();
        for (CommandeLigne ligne : lignes) {
            Device device = ligne.getDevice();
            linesBody.append("- ").append(device.getNom());
            if (device.getReference() != null && !device.getReference().isBlank()) {
                linesBody.append(" (réf. ").append(device.getReference()).append(")");
            }
            linesBody.append(" × ").append(ligne.getQuantite()).append('\n');
        }

        String adminName = validator != null
                ? displayUserName(validator)
                : "(Prénom Nom de l'administrateur)";
        String adminEmail = resolveUserEmail(validator, "(e-mail de l'administrateur)");
        String requesterEmail = resolveUserEmail(commande.getTechnicien(), "(e-mail du demandeur)");

        StringBuilder body = new StringBuilder();
        body.append("Bonjour,\n\n");
        body.append("Pouvez-vous nous faire un devis pour les pièces détachées suivantes :\n\n");
        body.append(linesBody);
        body.append('\n');
        body.append("Merci, bien à vous.\n");
        body.append(adminName).append('\n');
        body.append(adminEmail).append('\n');
        body.append(requesterEmail).append('\n');
        return body.toString();
    }

    private static String resolveUserEmail(User user, String fallback) {
        if (user == null || user.getEmail() == null || user.getEmail().isBlank()) {
            return fallback;
        }
        return user.getEmail().trim();
    }

    private Map<Long, List<CommandeLigne>> groupLinesBySfm(Commande commande) {
        Map<Long, List<CommandeLigne>> map = new LinkedHashMap<>();
        if (commande.getLignes() == null) {
            return map;
        }
        for (CommandeLigne ligne : commande.getLignes()) {
            Device device = ligne.getDevice();
            Long sfmId = device != null && device.getSfm() != null ? device.getSfm().getId() : null;
            map.computeIfAbsent(sfmId, k -> new ArrayList<>()).add(ligne);
        }
        return map;
    }

    private Set<String> resolveSfmRecipients(Sfm sfm) {
        Set<String> emails = new LinkedHashSet<>();
        if (sfm.getContacts() != null && !sfm.getContacts().isEmpty()) {
            for (SfmContact contact : sfm.getContacts()) {
                if (!contact.isReceiveOrderMails()) {
                    continue;
                }
                if (contact.getEmail() != null && !contact.getEmail().isBlank()) {
                    emails.add(contact.getEmail().trim());
                }
            }
            return emails;
        }
        // Ancien format : e-mail principal du SFM sans contacts
        if (sfm.getEmail() != null && !sfm.getEmail().isBlank()) {
            emails.add(sfm.getEmail().trim());
        }
        return emails;
    }

    private OrderRequestResponse toResponse(Commande entity) {
        List<OrderRequestLineResponse> lignes = entity.getLignes() == null
                ? List.of()
                : entity.getLignes().stream().map(this::toLineResponse).toList();

        OrderRequestLineResponse first = lignes.isEmpty() ? null : lignes.get(0);
        int totalQty = lignes.stream().mapToInt(OrderRequestLineResponse::getQuantite).sum();

        return OrderRequestResponse.builder()
                .id(entity.getId())
                .requestedBy(entity.getTechnicienNom())
                .technicienNom(entity.getTechnicienNom())
                .message(entity.getMessage())
                .status(entity.getStatus())
                .dateDemande(entity.getDateDemande())
                .createdAt(entity.getDateDemande())
                .totalPieces(lignes.size())
                .totalQuantite(totalQty)
                .lignes(lignes)
                .pieceNom(first != null ? first.getPieceNom() : null)
                .reference(first != null ? first.getReference() : null)
                .quantite(totalQty)
                .deviceId(first != null ? first.getDeviceId() : null)
                .photoUrl(first != null ? first.getPhotoUrl() : null)
                .build();
    }

    private OrderRequestLineResponse toLineResponse(CommandeLigne ligne) {
        Device device = ligne.getDevice();
        Sfm sfm = device != null ? device.getSfm() : null;
        return OrderRequestLineResponse.builder()
                .id(ligne.getId())
                .deviceId(device != null ? device.getId() : null)
                .pieceNom(device != null ? device.getNom() : null)
                .reference(device != null ? device.getReference() : null)
                .quantite(ligne.getQuantite())
                .photoUrl(device != null ? device.getPhotoUrl() : null)
                .sfmId(sfm != null ? sfm.getId() : null)
                .sfmNom(sfm != null ? sfm.getNom() : null)
                .build();
    }

    private static String displayUserName(User user) {
        String prenom = user.getPrenom() == null ? "" : user.getPrenom().trim();
        String nom = user.getNom() == null ? "" : user.getNom().trim();
        String full = (prenom + " " + nom).trim();
        return full.isEmpty() ? user.getUsername() : full;
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
