package com.devicemanager.service;

import com.devicemanager.dto.OrderRequestDto;
import com.devicemanager.dto.OrderRequestLineResponse;
import com.devicemanager.dto.OrderRequestResponse;
import com.devicemanager.entity.Commande;
import com.devicemanager.entity.CommandeLigne;
import com.devicemanager.entity.Device;
import com.devicemanager.entity.User;
import com.devicemanager.repository.CommandeRepository;
import com.devicemanager.repository.DeviceRepository;
import com.devicemanager.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
        var atelier = atelierService.requireCurrentAtelier();
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
                .technicienNom(technicien.getUsername())
                .message(request.getMessage().trim())
                .dateDemande(dateDemande)
                .status("SENT")
                .lignes(new ArrayList<>())
                .atelier(atelier)
                .build();

        StringBuilder linesBody = new StringBuilder();
        for (Map.Entry<Long, Integer> entry : quantities.entrySet()) {
            Device device = deviceRepository.findByIdWithRelations(entry.getKey(), atelierId)
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.BAD_REQUEST, "Pièce détachée introuvable: " + entry.getKey()));
            CommandeLigne ligne = CommandeLigne.builder()
                    .device(device)
                    .quantite(entry.getValue())
                    .build();
            commande.addLigne(ligne);
            linesBody.append("- ")
                    .append(device.getNom())
                    .append(" (réf. ").append(device.getReference()).append(")")
                    .append(" × ").append(entry.getValue())
                    .append('\n');
        }

        Commande saved = commandeRepository.save(commande);

        String subject = "Demande de commande — " + quantities.size() + " pièce(s)";
        String body = """
                Bonjour Administrateur,

                Le technicien %s demande une commande de pièces détachées.

                Date/heure : %s

                Pièces :
                %s
                Message :
                %s

                — DeviceManager
                """.formatted(
                technicien.getUsername(),
                dateDemande,
                linesBody,
                request.getMessage().trim()
        );

        mailService.sendOrderRequestToAdmin(subject, body);
        log.info("Demande de commande #{} créée par {} ({} pièce(s))",
                saved.getId(), technicien.getUsername(), quantities.size());
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<OrderRequestResponse> findAll() {
        Long atelierId = atelierService.requireCurrentAtelier().getId();
        return commandeRepository.findAllWithRelationsOrderByDateDesc(atelierId).stream()
                .map(this::toResponse)
                .toList();
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
        return OrderRequestLineResponse.builder()
                .id(ligne.getId())
                .deviceId(device != null ? device.getId() : null)
                .pieceNom(device != null ? device.getNom() : null)
                .reference(device != null ? device.getReference() : null)
                .quantite(ligne.getQuantite())
                .photoUrl(device != null ? device.getPhotoUrl() : null)
                .build();
    }
}
