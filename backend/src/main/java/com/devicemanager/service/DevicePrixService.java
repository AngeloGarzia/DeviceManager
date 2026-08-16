package com.devicemanager.service;

import com.devicemanager.dto.AiDevisOrderLineContext;
import com.devicemanager.dto.AiDevisPrixConfirmRequest;
import com.devicemanager.dto.AiDevisPrixConfirmResponse;
import com.devicemanager.dto.AiDevisPrixScanResponse;
import com.devicemanager.dto.AiDevisPrixSuggestion;
import com.devicemanager.dto.AiPrixDeviceContext;
import com.devicemanager.dto.AiPrixHistoryPoint;
import com.devicemanager.dto.AiPrixIncoherenceResult;
import com.devicemanager.dto.DevicePrixAlerteResponse;
import com.devicemanager.dto.DevicePrixObservationResponse;
import com.devicemanager.entity.Atelier;
import com.devicemanager.entity.Commande;
import com.devicemanager.entity.Device;
import com.devicemanager.entity.DevicePrixAlerte;
import com.devicemanager.entity.DevicePrixObservation;
import com.devicemanager.entity.PrixAlerteSeverity;
import com.devicemanager.entity.PrixAlerteStatus;
import com.devicemanager.entity.PrixSource;
import com.devicemanager.repository.CommandeRepository;
import com.devicemanager.repository.DevicePrixAlerteRepository;
import com.devicemanager.repository.DevicePrixObservationRepository;
import com.devicemanager.repository.DeviceRepository;
import com.devicemanager.security.DocumentUploadValidator;
import com.devicemanager.security.OrderStatuses;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Historique des prix pièces (issus des devis) et détection d'incohérences.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class DevicePrixService {

    static final BigDecimal SPIKE_RATIO = new BigDecimal("1.30");
    static final BigDecimal DROP_RATIO = new BigDecimal("0.70");

    private final DevicePrixObservationRepository observationRepository;
    private final DevicePrixAlerteRepository alerteRepository;
    private final DeviceRepository deviceRepository;
    private final CommandeRepository commandeRepository;
    private final AtelierService atelierService;
    private final AiAssistantService aiAssistantService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Transactional(readOnly = true)
    public List<DevicePrixObservationResponse> history(Long deviceId) {
        Long atelierId = atelierService.requireCurrentAtelier().getId();
        getDevice(deviceId, atelierId);
        return observationRepository
                .findByDeviceIdAndAtelierIdAndInvalidatedFalseOrderByObservedAtDesc(deviceId, atelierId)
                .stream()
                .map(this::toObservationResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<DevicePrixAlerteResponse> listAlertes(String status) {
        Long atelierId = atelierService.requireCurrentAtelier().getId();
        List<DevicePrixAlerte> list;
        if (status != null && !status.isBlank()) {
            PrixAlerteStatus st;
            try {
                st = PrixAlerteStatus.valueOf(status.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ex) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Statut alerte invalide");
            }
            list = alerteRepository.findByAtelierIdAndStatusOrderByCreatedAtDesc(atelierId, st);
        } else {
            list = alerteRepository.findByAtelierIdOrderByCreatedAtDesc(atelierId);
        }
        return list.stream().map(this::toAlerteResponse).toList();
    }

    public DevicePrixAlerteResponse acknowledge(Long id, String username) {
        return closeAlerte(id, username, PrixAlerteStatus.ACKNOWLEDGED);
    }

    public DevicePrixAlerteResponse dismiss(Long id, String username) {
        return closeAlerte(id, username, PrixAlerteStatus.DISMISSED);
    }

    public AiDevisPrixScanResponse analyzeDevisPrices(Long orderId, MultipartFile file) {
        DocumentUploadValidator.Kind kind = DocumentUploadValidator.validatePdfOrImage(file, "devis");
        if (kind != DocumentUploadValidator.Kind.PDF) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "L'extraction des prix est disponible uniquement pour les PDF textuels");
        }
        Long atelierId = atelierService.requireCurrentAtelier().getId();
        Commande commande = commandeRepository.findByIdWithRelations(orderId, atelierId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Demande de commande introuvable"));
        if (!OrderStatuses.canAttachDevis(commande.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Analyse des prix possible uniquement après validation de la commande.");
        }

        List<AiDevisOrderLineContext> lines = commande.getLignes() == null
                ? List.of()
                : commande.getLignes().stream()
                .filter(l -> l.getDevice() != null)
                .map(l -> AiDevisOrderLineContext.builder()
                        .deviceId(l.getDevice().getId())
                        .nom(l.getDevice().getNom())
                        .reference(l.getDevice().getReference())
                        .build())
                .toList();

        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Devis PDF illisible");
        }

        AiDevisPrixScanResponse scan = aiAssistantService.analyzeDevisPrices(bytes, lines);
        Map<Long, Device> devices = lines.stream()
                .map(AiDevisOrderLineContext::getDeviceId)
                .filter(Objects::nonNull)
                .distinct()
                .map(id -> deviceRepository.findByIdWithRelations(id, atelierId).orElse(null))
                .filter(Objects::nonNull)
                .collect(Collectors.toMap(Device::getId, d -> d, (a, b) -> a));

        if (scan.getSuggestions() != null) {
            for (AiDevisPrixSuggestion s : scan.getSuggestions()) {
                Device d = devices.get(s.getDeviceId());
                if (d != null) {
                    s.setLastUnitPriceHt(d.getLastUnitPriceHt());
                }
            }
        }
        return scan;
    }

    public AiDevisPrixConfirmResponse confirmDevisPrices(
            Long orderId,
            AiDevisPrixConfirmRequest request,
            String adminUsername) {
        Long atelierId = atelierService.requireCurrentAtelier().getId();
        Atelier atelier = atelierService.requireCurrentAtelier();
        Commande commande = commandeRepository.findByIdWithRelations(orderId, atelierId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Demande de commande introuvable"));
        if (!OrderStatuses.canAttachDevis(commande.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Confirmation des prix possible uniquement après validation de la commande.");
        }

        Set<Long> orderDeviceIds = commande.getLignes() == null
                ? Set.of()
                : commande.getLignes().stream()
                .filter(l -> l.getDevice() != null)
                .map(l -> l.getDevice().getId())
                .collect(Collectors.toSet());

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime observedAt = commande.getDevisUploadedAt() != null
                ? commande.getDevisUploadedAt()
                : now;

        List<String> errors = new ArrayList<>();
        List<DevicePrixObservation> created = new ArrayList<>();
        List<AiPrixDeviceContext> aiContexts = new ArrayList<>();

        for (AiDevisPrixConfirmRequest.Item item : request.getItems()) {
            if (item.getDeviceId() == null || !orderDeviceIds.contains(item.getDeviceId())) {
                errors.add("Pièce hors commande (id=" + item.getDeviceId() + ")");
                continue;
            }
            if (item.getUnitPriceHt() == null || item.getUnitPriceHt().compareTo(BigDecimal.ZERO) < 0) {
                errors.add("Prix invalide pour la pièce id=" + item.getDeviceId());
                continue;
            }
            Device device = deviceRepository.findByIdWithRelations(item.getDeviceId(), atelierId)
                    .orElse(null);
            if (device == null) {
                errors.add("Pièce introuvable id=" + item.getDeviceId());
                continue;
            }

            BigDecimal price = item.getUnitPriceHt().setScale(2, RoundingMode.HALF_UP);
            List<DevicePrixObservation> history = observationRepository
                    .findTop20ByDeviceIdAndAtelierIdAndInvalidatedFalseOrderByObservedAtDesc(
                            device.getId(), atelierId);

            DevicePrixObservation obs = DevicePrixObservation.builder()
                    .atelier(atelier)
                    .device(device)
                    .commande(commande)
                    .source(PrixSource.DEVIS)
                    .unitPriceHt(price)
                    .currency("EUR")
                    .quantityOnQuote(item.getQuantityOnQuote())
                    .devisDesignation(trimTo(item.getDevisDesignation(), 255))
                    .devisReference(trimTo(item.getDevisReference(), 120))
                    .observedAt(observedAt)
                    .confirmedAt(now)
                    .confirmedBy(adminUsername)
                    .invalidated(false)
                    .build();
            DevicePrixObservation saved = observationRepository.save(obs);
            created.add(saved);

            device.setLastUnitPriceHt(price);
            device.setLastUnitPriceAt(observedAt);
            deviceRepository.save(device);

            List<String> signals = detectSignals(price, history);
            PrixAlerteSeverity severity = severityFromSignals(signals);

            if (severity != null) {
                DevicePrixAlerte alerte = DevicePrixAlerte.builder()
                        .atelier(atelier)
                        .device(device)
                        .observation(saved)
                        .severity(severity)
                        .signalsJson(toJson(signals))
                        .status(PrixAlerteStatus.OPEN)
                        .createdAt(now)
                        .build();
                alerteRepository.save(alerte);

                aiContexts.add(AiPrixDeviceContext.builder()
                        .deviceId(device.getId())
                        .nom(device.getNom())
                        .reference(device.getReference())
                        .newUnitPriceHt(price)
                        .history(history.stream()
                                .map(h -> AiPrixHistoryPoint.builder()
                                        .unitPriceHt(h.getUnitPriceHt())
                                        .observedAt(h.getObservedAt() != null
                                                ? h.getObservedAt().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                                                : null)
                                        .commandeId(h.getCommande() != null ? h.getCommande().getId() : null)
                                        .build())
                                .toList())
                        .build());
            }
        }

        enrichAlertesWithAi(atelierId, aiContexts);

        List<DevicePrixAlerteResponse> alertes = alerteRepository
                .findByAtelierIdAndStatusOrderByCreatedAtDesc(atelierId, PrixAlerteStatus.OPEN)
                .stream()
                .filter(a -> created.stream().anyMatch(o -> o.getId().equals(a.getObservation().getId())))
                .map(this::toAlerteResponse)
                .toList();

        log.info("Prix devis confirmés — commande id={} count={} alertes={} par={}",
                orderId, created.size(), alertes.size(), adminUsername);

        return AiDevisPrixConfirmResponse.builder()
                .order(null)
                .confirmedCount(created.size())
                .observations(created.stream().map(this::toObservationResponse).toList())
                .alertes(alertes)
                .errors(errors)
                .build();
    }

    private void enrichAlertesWithAi(Long atelierId, List<AiPrixDeviceContext> contexts) {
        if (contexts.isEmpty() || !aiAssistantService.isEnabled()) {
            return;
        }
        try {
            List<AiPrixIncoherenceResult> results = aiAssistantService.analyzePrixIncoherences(contexts);
            Map<Long, AiPrixIncoherenceResult> byDevice = results.stream()
                    .filter(r -> r.getDeviceId() != null)
                    .collect(Collectors.toMap(AiPrixIncoherenceResult::getDeviceId, r -> r, (a, b) -> a));
            for (AiPrixDeviceContext ctx : contexts) {
                AiPrixIncoherenceResult r = byDevice.get(ctx.getDeviceId());
                if (r == null || "OK".equalsIgnoreCase(r.getSeverity())) {
                    continue;
                }
                List<DevicePrixAlerte> open = alerteRepository
                        .findByDeviceIdAndAtelierIdAndStatusOrderByCreatedAtDesc(
                                ctx.getDeviceId(), atelierId, PrixAlerteStatus.OPEN);
                if (open.isEmpty()) {
                    continue;
                }
                DevicePrixAlerte latest = open.getFirst();
                if ("ALERT".equalsIgnoreCase(r.getSeverity())) {
                    latest.setSeverity(PrixAlerteSeverity.ALERT);
                } else if (latest.getSeverity() == PrixAlerteSeverity.WATCH
                        && "WATCH".equalsIgnoreCase(r.getSeverity())) {
                    latest.setSeverity(PrixAlerteSeverity.WATCH);
                }
                latest.setAiSummary(r.getSummary() != null ? r.getSummary() : joinReasons(r.getReasons()));
                latest.setAiPayload(toJson(r));
                alerteRepository.save(latest);
            }
        } catch (Exception ex) {
            log.warn("Enrichissement IA alertes prix ignoré: {}", ex.getMessage());
        }
    }

    static List<String> detectSignals(BigDecimal newPrice, List<DevicePrixObservation> history) {
        Set<String> signals = new LinkedHashSet<>();
        if (history == null || history.isEmpty()) {
            signals.add("NO_BASELINE");
            return List.copyOf(signals);
        }
        if (history.size() < 2) {
            signals.add("LOW_SAMPLE");
        }
        DevicePrixObservation last = history.getFirst();
        BigDecimal prev = last.getUnitPriceHt();
        if (prev != null && prev.compareTo(BigDecimal.ZERO) > 0) {
            if (newPrice.compareTo(prev.multiply(SPIKE_RATIO)) > 0) {
                signals.add("SPIKE");
            }
            if (newPrice.compareTo(prev.multiply(DROP_RATIO)) < 0) {
                signals.add("DROP");
            }
        }
        return List.copyOf(signals);
    }

    static PrixAlerteSeverity severityFromSignals(List<String> signals) {
        if (signals == null || signals.isEmpty()) {
            return null;
        }
        if (signals.contains("SPIKE") || signals.contains("DROP")) {
            return PrixAlerteSeverity.ALERT;
        }
        if (signals.contains("LOW_SAMPLE")) {
            return PrixAlerteSeverity.WATCH;
        }
        // NO_BASELINE seul : pas d'alerte
        return null;
    }

    private DevicePrixAlerteResponse closeAlerte(Long id, String username, PrixAlerteStatus status) {
        Long atelierId = atelierService.requireCurrentAtelier().getId();
        DevicePrixAlerte alerte = alerteRepository.findByIdAndAtelierId(id, atelierId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Alerte prix introuvable"));
        alerte.setStatus(status);
        alerte.setAckBy(username);
        alerte.setAckAt(LocalDateTime.now());
        return toAlerteResponse(alerteRepository.save(alerte));
    }

    private Device getDevice(Long deviceId, Long atelierId) {
        return deviceRepository.findByIdWithRelations(deviceId, atelierId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Pièce détachée introuvable"));
    }

    private DevicePrixObservationResponse toObservationResponse(DevicePrixObservation o) {
        return DevicePrixObservationResponse.builder()
                .id(o.getId())
                .deviceId(o.getDevice() != null ? o.getDevice().getId() : null)
                .deviceNom(o.getDevice() != null ? o.getDevice().getNom() : null)
                .commandeId(o.getCommande() != null ? o.getCommande().getId() : null)
                .source(o.getSource() != null ? o.getSource().name() : PrixSource.DEVIS.name())
                .unitPriceHt(o.getUnitPriceHt())
                .currency(o.getCurrency())
                .quantityOnQuote(o.getQuantityOnQuote())
                .devisDesignation(o.getDevisDesignation())
                .devisReference(o.getDevisReference())
                .observedAt(o.getObservedAt())
                .confirmedAt(o.getConfirmedAt())
                .confirmedBy(o.getConfirmedBy())
                .invalidated(o.isInvalidated())
                .build();
    }

    private DevicePrixAlerteResponse toAlerteResponse(DevicePrixAlerte a) {
        DevicePrixObservation obs = a.getObservation();
        Device device = a.getDevice();
        return DevicePrixAlerteResponse.builder()
                .id(a.getId())
                .deviceId(device != null ? device.getId() : null)
                .deviceNom(device != null ? device.getNom() : null)
                .deviceReference(device != null ? device.getReference() : null)
                .observationId(obs != null ? obs.getId() : null)
                .unitPriceHt(obs != null ? obs.getUnitPriceHt() : null)
                .severity(a.getSeverity() != null ? a.getSeverity().name() : null)
                .signals(parseSignals(a.getSignalsJson()))
                .aiSummary(a.getAiSummary())
                .status(a.getStatus() != null ? a.getStatus().name() : null)
                .createdAt(a.getCreatedAt())
                .ackBy(a.getAckBy())
                .ackAt(a.getAckAt())
                .build();
    }

    private List<String> parseSignals(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json,
                    objectMapper.getTypeFactory().constructCollectionType(List.class, String.class));
        } catch (Exception ex) {
            return List.of();
        }
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            return null;
        }
    }

    private static String joinReasons(List<String> reasons) {
        if (reasons == null || reasons.isEmpty()) {
            return null;
        }
        return String.join(" · ", reasons);
    }

    private static String trimTo(String value, int max) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String t = value.trim();
        return t.length() > max ? t.substring(0, max) : t;
    }
}
