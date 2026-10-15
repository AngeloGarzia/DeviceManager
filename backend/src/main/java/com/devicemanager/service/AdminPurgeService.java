package com.devicemanager.service;

import com.devicemanager.dto.AdminPurgeExecuteRequest;
import com.devicemanager.dto.AdminPurgePlanNode;
import com.devicemanager.dto.AdminPurgePlanResponse;
import com.devicemanager.dto.AdminPurgeResultResponse;
import com.devicemanager.entity.Commande;
import com.devicemanager.entity.CommandeLigne;
import com.devicemanager.entity.Device;
import com.devicemanager.entity.DeviceDocument;
import com.devicemanager.entity.DevicePhoto;
import com.devicemanager.entity.Intervention;
import com.devicemanager.entity.InterventionLigne;
import com.devicemanager.entity.StockMouvement;
import com.devicemanager.repository.CommandeLigneRepository;
import com.devicemanager.repository.DevicePrixAlerteRepository;
import com.devicemanager.repository.DevicePrixObservationRepository;
import com.devicemanager.repository.DeviceRepository;
import com.devicemanager.repository.InterventionLigneRepository;
import com.devicemanager.repository.InterventionRepository;
import com.devicemanager.repository.StockMouvementRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * Purges en chaîne Super Admin — préserve l'intégrité et l'historique métier.
 * <p>
 * Politique DEVICE (v1) :
 * <ul>
 *   <li>bons d'intervention contenant la pièce → {@code archived}</li>
 *   <li>lignes BI / stock / commandes reçues → détachement FK (snapshots conservés)</li>
 *   <li>lignes de commandes non reçues → suppression de la ligne</li>
 *   <li>historique prix → suppression</li>
 *   <li>pièce + fichiers → suppression</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class AdminPurgeService {

    public static final String ROOT_DEVICE = "DEVICE";

    private final DeviceRepository deviceRepository;
    private final InterventionLigneRepository interventionLigneRepository;
    private final InterventionRepository interventionRepository;
    private final StockMouvementRepository stockMouvementRepository;
    private final CommandeLigneRepository commandeLigneRepository;
    private final DevicePrixAlerteRepository devicePrixAlerteRepository;
    private final DevicePrixObservationRepository devicePrixObservationRepository;
    private final AtelierService atelierService;
    private final StorageService storageService;
    private final Clock clock;

    @Transactional(readOnly = true)
    public AdminPurgePlanResponse preview(String rootType, Long rootId) {
        String type = normalizeRootType(rootType);
        if (ROOT_DEVICE.equals(type)) {
            return previewDevice(rootId);
        }
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Type de purge non supporté : " + rootType + " (v1 : DEVICE)");
    }

    public AdminPurgeResultResponse execute(AdminPurgeExecuteRequest request, String username) {
        String type = normalizeRootType(request.getRootType());
        AdminPurgePlanResponse plan = preview(type, request.getRootId());
        if (!plan.isExecutable()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    plan.getBlockers().isEmpty()
                            ? "Purge impossible."
                            : plan.getBlockers().get(0));
        }
        String expected = plan.getConfirmPhrase();
        if (expected == null || !expected.equals(request.getConfirmPhrase() == null
                ? null
                : request.getConfirmPhrase().trim())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Phrase de confirmation incorrecte. Attendu : " + expected);
        }
        if (ROOT_DEVICE.equals(type)) {
            return executeDevice(request.getRootId(), plan, username);
        }
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Type non supporté");
    }

    private AdminPurgePlanResponse previewDevice(Long deviceId) {
        Device device = requireDeviceInCurrentAtelier(deviceId);
        String label = deviceLabel(device);
        String confirm = "SUPPRIMER DEVICE #" + device.getId();

        List<AdminPurgePlanNode> nodes = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        List<String> blockers = new ArrayList<>();

        List<InterventionLigne> lignes = interventionLigneRepository.findByDeviceIdWithIntervention(deviceId);
        Set<Intervention> bons = new LinkedHashSet<>();
        for (InterventionLigne l : lignes) {
            Intervention bi = l.getIntervention();
            if (bi != null) {
                bons.add(bi);
            }
            nodes.add(AdminPurgePlanNode.builder()
                    .entityType("INTERVENTION_LIGNE")
                    .entityId(l.getId())
                    .label("Ligne BI · " + nullToDash(l.getPieceNom()))
                    .action("DETACH")
                    .detail("device_id → null (snapshot conservé)")
                    .build());
        }
        for (Intervention bi : bons) {
            nodes.add(AdminPurgePlanNode.builder()
                    .entityType("INTERVENTION")
                    .entityId(bi.getId())
                    .label(bi.getNumero())
                    .action(bi.isArchived() ? "SKIP" : "ARCHIVE")
                    .detail(bi.isArchived()
                            ? "Déjà archivé"
                            : "Bon archivé — historique conservé")
                    .build());
        }

        long stockCount = stockMouvementRepository.countByDeviceId(deviceId);
        if (stockCount > 0) {
            nodes.add(AdminPurgePlanNode.builder()
                    .entityType("STOCK_MOUVEMENT")
                    .entityId(null)
                    .label(stockCount + " mouvement(s) de stock")
                    .action("DETACH")
                    .detail("Historique conservé (device_id → null)")
                    .build());
            warnings.add("L'historique de stock est conservé (non reconstitué).");
        }

        List<CommandeLigne> cmdLignes = commandeLigneRepository.findByDeviceIdWithCommande(deviceId);
        for (CommandeLigne cl : cmdLignes) {
            Commande c = cl.getCommande();
            String status = c != null ? c.getStatus() : "?";
            boolean received = "RECEIVED".equalsIgnoreCase(status);
            nodes.add(AdminPurgePlanNode.builder()
                    .entityType("COMMANDE_LIGNE")
                    .entityId(cl.getId())
                    .label("Commande #" + (c != null ? c.getId() : "?") + " · " + status)
                    .action(received ? "DETACH" : "DELETE")
                    .detail(received
                            ? "Commande reçue : ligne détachée (snapshot)"
                            : "Commande non reçue : ligne retirée")
                    .build());
        }

        long alertes = devicePrixAlerteRepository.countByDeviceId(deviceId);
        long obs = devicePrixObservationRepository.countByDeviceId(deviceId);
        if (alertes > 0 || obs > 0) {
            nodes.add(AdminPurgePlanNode.builder()
                    .entityType("DEVICE_PRIX")
                    .entityId(deviceId)
                    .label(alertes + " alerte(s), " + obs + " observation(s) prix")
                    .action("DELETE")
                    .detail("Historique prix de la pièce")
                    .build());
        }

        nodes.add(AdminPurgePlanNode.builder()
                .entityType("DEVICE")
                .entityId(device.getId())
                .label(label)
                .action("DELETE")
                .detail("Pièce + photos / documents")
                .build());

        long toArchive = bons.stream().filter(b -> !b.isArchived()).count();
        if (toArchive > 0) {
            warnings.add(toArchive + " bon(s) d'intervention seront archivés (pas supprimés).");
        }

        return AdminPurgePlanResponse.builder()
                .rootType(ROOT_DEVICE)
                .rootId(device.getId())
                .rootLabel(label)
                .nodes(nodes)
                .warnings(warnings)
                .blockers(blockers)
                .executable(blockers.isEmpty())
                .confirmPhrase(confirm)
                .build();
    }

    private AdminPurgeResultResponse executeDevice(Long deviceId, AdminPurgePlanResponse plan, String username) {
        Device device = requireDeviceInCurrentAtelier(deviceId);
        String label = deviceLabel(device);
        LocalDateTime now = LocalDateTime.now(clock);
        String reason = "Purge pièce #" + deviceId + " par " + username;
        List<AdminPurgePlanNode> applied = new ArrayList<>();

        List<InterventionLigne> lignes = interventionLigneRepository.findByDeviceIdWithIntervention(deviceId);
        Set<Intervention> bons = new LinkedHashSet<>();
        for (InterventionLigne l : lignes) {
            if (l.getIntervention() != null) {
                bons.add(l.getIntervention());
            }
            if (l.getPieceNom() == null || l.getPieceNom().isBlank()) {
                l.setPieceNom(device.getNom());
            }
            if (l.getPieceReference() == null) {
                l.setPieceReference(device.getReference());
            }
            l.setDevice(null);
            interventionLigneRepository.save(l);
            applied.add(AdminPurgePlanNode.builder()
                    .entityType("INTERVENTION_LIGNE")
                    .entityId(l.getId())
                    .label(l.getPieceNom())
                    .action("DETACH")
                    .build());
        }
        for (Intervention bi : bons) {
            if (!bi.isArchived()) {
                bi.setArchived(true);
                bi.setArchivedAt(now);
                bi.setArchivedReason(reason);
                interventionRepository.save(bi);
                applied.add(AdminPurgePlanNode.builder()
                        .entityType("INTERVENTION")
                        .entityId(bi.getId())
                        .label(bi.getNumero())
                        .action("ARCHIVE")
                        .build());
            }
        }

        List<StockMouvement> mouvements = stockMouvementRepository.findByDeviceId(deviceId);
        for (StockMouvement m : mouvements) {
            if (m.getPieceNom() == null || m.getPieceNom().isBlank()) {
                m.setPieceNom(device.getNom());
            }
            if (m.getPieceReference() == null) {
                m.setPieceReference(device.getReference());
            }
            m.setDevice(null);
            stockMouvementRepository.save(m);
        }
        if (!mouvements.isEmpty()) {
            applied.add(AdminPurgePlanNode.builder()
                    .entityType("STOCK_MOUVEMENT")
                    .label(mouvements.size() + " mouvement(s)")
                    .action("DETACH")
                    .build());
        }

        List<CommandeLigne> cmdLignes = commandeLigneRepository.findByDeviceIdWithCommande(deviceId);
        for (CommandeLigne cl : cmdLignes) {
            Commande c = cl.getCommande();
            boolean received = c != null && "RECEIVED".equalsIgnoreCase(c.getStatus());
            if (received) {
                if (cl.getPieceNom() == null || cl.getPieceNom().isBlank()) {
                    cl.setPieceNom(device.getNom());
                }
                if (cl.getPieceReference() == null) {
                    cl.setPieceReference(device.getReference());
                }
                cl.setDevice(null);
                commandeLigneRepository.save(cl);
                applied.add(AdminPurgePlanNode.builder()
                        .entityType("COMMANDE_LIGNE")
                        .entityId(cl.getId())
                        .action("DETACH")
                        .label("Commande #" + c.getId())
                        .build());
            } else {
                Long ligneId = cl.getId();
                if (c != null && c.getLignes() != null) {
                    c.getLignes().removeIf(x -> Objects.equals(x.getId(), ligneId));
                }
                commandeLigneRepository.delete(cl);
                applied.add(AdminPurgePlanNode.builder()
                        .entityType("COMMANDE_LIGNE")
                        .entityId(ligneId)
                        .action("DELETE")
                        .label(c != null ? "Commande #" + c.getId() : "ligne")
                        .build());
            }
        }

        long alertes = devicePrixAlerteRepository.countByDeviceId(deviceId);
        long obs = devicePrixObservationRepository.countByDeviceId(deviceId);
        devicePrixAlerteRepository.deleteByDeviceId(deviceId);
        devicePrixObservationRepository.deleteByDeviceId(deviceId);
        if (alertes > 0 || obs > 0) {
            applied.add(AdminPurgePlanNode.builder()
                    .entityType("DEVICE_PRIX")
                    .entityId(deviceId)
                    .action("DELETE")
                    .label(alertes + " alerte(s), " + obs + " obs.")
                    .build());
        }

        for (DevicePhoto photo : device.getPhotos()) {
            if (photo.getPhotoKey() != null) {
                storageService.delete(photo.getPhotoKey());
            }
        }
        if (device.getDocuments() != null) {
            for (DeviceDocument doc : device.getDocuments()) {
                if (doc.getFileKey() != null) {
                    storageService.delete(doc.getFileKey());
                }
            }
        }
        if (device.getPhotoKey() != null
                && device.getPhotos().stream().noneMatch(p -> Objects.equals(p.getPhotoKey(), device.getPhotoKey()))) {
            storageService.delete(device.getPhotoKey());
        }
        deviceRepository.delete(device);
        applied.add(AdminPurgePlanNode.builder()
                .entityType("DEVICE")
                .entityId(deviceId)
                .label(label)
                .action("DELETE")
                .build());

        log.info("Purge Super Admin — DEVICE id={} label={} par={} nodes={}",
                deviceId, label, username, applied.size());

        return AdminPurgeResultResponse.builder()
                .rootType(ROOT_DEVICE)
                .rootId(deviceId)
                .applied(applied)
                .message("Pièce « " + label + " » purgée. Bons archivés, historique stock conservé.")
                .build();
    }

    private Device requireDeviceInCurrentAtelier(Long deviceId) {
        Long atelierId = atelierService.requireCurrentAtelier().getId();
        return deviceRepository.findByIdWithRelations(deviceId, atelierId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Pièce introuvable dans l'atelier courant"));
    }

    private static String normalizeRootType(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "rootType obligatoire");
        }
        return raw.trim().toUpperCase(Locale.ROOT);
    }

    private static String deviceLabel(Device d) {
        String ref = d.getReference();
        if (ref != null && !ref.isBlank()) {
            return d.getNom() + " (" + ref + ")";
        }
        return d.getNom();
    }

    private static String nullToDash(String v) {
        return v == null || v.isBlank() ? "—" : v;
    }
}
