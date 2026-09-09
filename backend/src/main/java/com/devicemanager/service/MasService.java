package com.devicemanager.service;

import com.devicemanager.dto.AiRegleJeuxScanResponse;
import com.devicemanager.dto.DenoRequest;
import com.devicemanager.dto.DenoResponse;
import com.devicemanager.dto.MarqueMasRequest;
import com.devicemanager.dto.MarqueMasResponse;
import com.devicemanager.dto.MasRequest;
import com.devicemanager.dto.MasResponse;
import com.devicemanager.dto.RegleJeuxMasLinkRequest;
import com.devicemanager.dto.RegleJeuxMasSummary;
import com.devicemanager.dto.RegleJeuxRequest;
import com.devicemanager.dto.RegleJeuxResponse;
import com.devicemanager.entity.Atelier;
import com.devicemanager.entity.Deno;
import com.devicemanager.entity.MarqueMas;
import com.devicemanager.entity.Mas;
import com.devicemanager.entity.MasStatut;
import com.devicemanager.entity.RegleJeux;
import com.devicemanager.repository.DenoRepository;
import com.devicemanager.repository.MarqueMasRepository;
import com.devicemanager.repository.MasRepository;
import com.devicemanager.repository.RegleJeuxRepository;
import com.devicemanager.security.DocumentUploadValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.Normalizer;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Service métier des MAS (Machines À Sous), marques et dénominations associées.
 * <p>
 * Les MAS sont rattachées à l'atelier courant ({@code X-Atelier-Id}) ;
 * les marques et dénominations constituent un référentiel global partagé.
 */
@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class MasService {

    /** Libellé d'affichage pour une MAS multi-dénomination. */
    public static final String MULTI_DENO_LABEL = "MultiDéno";

    private final MasRepository masRepository;
    private final MarqueMasRepository marqueMasRepository;
    private final DenoRepository denoRepository;
    private final RegleJeuxRepository regleJeuxRepository;
    private final AiAssistantService aiAssistantService;
    private final AtelierService atelierService;
    private final StorageService storageService;
    private final FitService fitService;
    private final AtelierMemoirePublisher atelierMemoirePublisher;

    @Transactional(readOnly = true)
    public List<MasResponse> findAll(String q) {
        Long atelierId = atelierService.requireCurrentAtelier().getId();
        List<Mas> list = (q == null || q.isBlank())
                ? masRepository.findAllByAtelierId(atelierId)
                : masRepository.search(atelierId, q.trim());
        return list.stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public MasResponse findById(Long id) {
        return toResponse(getEntity(id));
    }

    @Transactional(readOnly = true)
    public List<MarqueMasResponse> listMarques() {
        return marqueMasRepository.findAllByOrderByLabelAsc().stream()
                .sorted(Comparator.comparing(MarqueMas::getLabel, String.CASE_INSENSITIVE_ORDER))
                .map(this::toMarqueResponse)
                .toList();
    }

    public MarqueMasResponse createMarque(MarqueMasRequest request) {
        String label = request.getLabel().trim();
        if (marqueMasRepository.existsByLabelIgnoreCase(label)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Nom de marque déjà utilisé");
        }
        String code = toCode(label);
        String uniqueCode = code;
        int i = 2;
        while (marqueMasRepository.existsByCodeIgnoreCase(uniqueCode)) {
            uniqueCode = code + "_" + i++;
        }
        MarqueMas saved = marqueMasRepository.save(MarqueMas.builder()
                .code(uniqueCode)
                .label(label)
                .build());
        log.info("Création en base — Marque MAS id={} label={} code={}",
                saved.getId(), saved.getLabel(), saved.getCode());
        return toMarqueResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<DenoResponse> listDenos() {
        return denoRepository.findAllByOrderByValeurAsc().stream()
                .map(this::toDenoResponse)
                .toList();
    }

    public DenoResponse createDeno(DenoRequest request) {
        BigDecimal valeur = request.getValeur().setScale(4, RoundingMode.HALF_UP);
        if (denoRepository.existsByValeur(valeur)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Cette dénomination existe déjà");
        }
        String label = request.getLabel() == null || request.getLabel().isBlank()
                ? formatDenoLabel(valeur)
                : request.getLabel().trim();
        if (denoRepository.existsByLabelIgnoreCase(label)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Libellé de dénomination déjà utilisé");
        }
        Deno saved = denoRepository.save(Deno.builder()
                .valeur(valeur)
                .label(label)
                .build());
        log.info("Création en base — Deno id={} valeur={} label={}",
                saved.getId(), saved.getValeur(), saved.getLabel());
        return toDenoResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<RegleJeuxResponse> listReglesJeux() {
        return regleJeuxRepository.findAllByOrderByLabelAsc().stream()
                .sorted(Comparator.comparing(RegleJeux::getLabel, String.CASE_INSENSITIVE_ORDER))
                .map(this::toRegleJeuxResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public RegleJeuxResponse findRegleJeuxById(Long id) {
        RegleJeux regle = getRegleJeuxEntity(id);
        Long atelierId = atelierService.requireCurrentAtelier().getId();
        return toRegleJeuxResponseDetailed(regle, atelierId);
    }

    /**
     * Rattache des MAS de l'atelier courant à une règle de jeux (remplace les liens existants pour cet atelier).
     * Chaque MAS détachée doit conserver au moins une autre règle.
     */
    public RegleJeuxResponse linkMasToRegleJeux(Long regleId, RegleJeuxMasLinkRequest request) {
        RegleJeux regle = getRegleJeuxEntity(regleId);
        Long atelierId = atelierService.requireCurrentAtelier().getId();
        List<Long> rawIds = request.getMasIds() == null ? List.of() : request.getMasIds();
        Set<Long> targetIds = new LinkedHashSet<>(rawIds);

        if (!targetIds.isEmpty()) {
            List<Mas> found = masRepository.findAllByIdInAndAtelierId(targetIds, atelierId);
            if (found.size() != targetIds.size()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Une ou plusieurs MAS sont introuvables dans cet atelier");
            }
        }

        List<Mas> currentlyLinked = masRepository.findAllByRegleJeuxIdAndAtelierId(regleId, atelierId);
        Set<Long> currentIds = currentlyLinked.stream().map(Mas::getId).collect(Collectors.toSet());

        Set<Long> toRemove = new HashSet<>(currentIds);
        toRemove.removeAll(targetIds);

        Set<Long> toAdd = new HashSet<>(targetIds);
        toAdd.removeAll(currentIds);

        for (Mas mas : currentlyLinked) {
            if (!toRemove.contains(mas.getId())) {
                continue;
            }
            mas.getReglesJeux().removeIf(r -> r.getId().equals(regleId));
            if (mas.getReglesJeux().isEmpty()) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "La MAS " + mas.getNumero() + " doit conserver au moins une règle de jeux");
            }
            masRepository.save(mas);
        }

        for (Long masId : toAdd) {
            Mas mas = getEntity(masId);
            mas.getReglesJeux().add(regle);
            masRepository.save(mas);
        }

        log.info("Rattachement MAS — Règle de jeux id={} mas={}", regleId, targetIds.size());
        return toRegleJeuxResponseDetailed(regle, atelierId);
    }

    /**
     * Crée une règle de jeux dans le catalogue global avec son PDF.
     */
    public RegleJeuxResponse createRegleJeux(String labelRaw, String descriptionRaw, MultipartFile file) {
        if (labelRaw == null || labelRaw.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Le libellé de la règle de jeux est obligatoire");
        }
        DocumentUploadValidator.validatePdf(file, "règle de jeux");
        String label = labelRaw.trim();
        if (label.length() > 200) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Le libellé de la règle de jeux ne doit pas dépasser 200 caractères");
        }
        if (regleJeuxRepository.existsByLabelIgnoreCase(label)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Cette règle de jeux existe déjà");
        }
        String code = toCode(label);
        String uniqueCode = code;
        int i = 2;
        while (regleJeuxRepository.existsByCodeIgnoreCase(uniqueCode)) {
            uniqueCode = code + "_" + i++;
        }
        StorageService.StoredObject stored = storageService.store(file);
        String original = normalizeOriginalFilename(file.getOriginalFilename(), "regle-jeux.pdf");
        String description = trimToNull(descriptionRaw);
        if (description != null && description.length() > 500) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "La description ne doit pas dépasser 500 caractères");
        }
        RegleJeux saved = regleJeuxRepository.save(RegleJeux.builder()
                .code(uniqueCode)
                .label(label)
                .description(description)
                .fileKey(stored.key())
                .fileUrl(stored.url())
                .originalName(original)
                .contentType(stored.contentType() != null ? stored.contentType() : file.getContentType())
                .fileSize(stored.size())
                .uploadedAt(LocalDateTime.now())
                .build());
        log.info("Création en base — Règle de jeux id={} label={} code={}",
                saved.getId(), saved.getLabel(), saved.getCode());
        return toRegleJeuxResponse(saved);
    }

    /**
     * Analyse un PDF de règle de jeux via l'IA (libellé + description proposés).
     * Si l'IA est indisponible, renvoie {@code enabled=false} pour saisie manuelle.
     */
    public AiRegleJeuxScanResponse analyzeRegleJeuxPdf(MultipartFile file) {
        DocumentUploadValidator.validatePdf(file, "règle de jeux");
        try {
            return aiAssistantService.analyzeRegleJeuxPdf(file);
        } catch (ResponseStatusException ex) {
            if (ex.getStatusCode() == HttpStatus.SERVICE_UNAVAILABLE) {
                return AiRegleJeuxScanResponse.builder()
                        .enabled(false)
                        .notes(ex.getReason())
                        .build();
            }
            throw ex;
        }
    }

    public RegleJeuxResponse updateRegleJeux(Long id, RegleJeuxRequest request) {
        RegleJeux entity = getRegleJeuxEntity(id);
        String label = request.getLabel().trim();
        if (regleJeuxRepository.existsByLabelIgnoreCaseAndIdNot(label, id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Cette règle de jeux existe déjà");
        }
        String description = trimToNull(request.getDescription());
        if (description != null && description.length() > 500) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "La description ne doit pas dépasser 500 caractères");
        }
        entity.setLabel(label);
        entity.setDescription(description);
        RegleJeux saved = regleJeuxRepository.save(entity);
        log.info("Modification en base — Règle de jeux id={} label={}", saved.getId(), saved.getLabel());
        return toRegleJeuxResponse(saved);
    }

    public RegleJeuxResponse replaceRegleJeuxDocument(Long id, MultipartFile file) {
        DocumentUploadValidator.validatePdf(file, "règle de jeux");
        RegleJeux entity = getRegleJeuxEntity(id);
        String previousKey = entity.getFileKey();
        StorageService.StoredObject stored = storageService.store(file);
        String original = normalizeOriginalFilename(file.getOriginalFilename(), "regle-jeux.pdf");
        entity.setFileKey(stored.key());
        entity.setFileUrl(stored.url());
        entity.setOriginalName(original);
        entity.setContentType(stored.contentType() != null ? stored.contentType() : file.getContentType());
        entity.setFileSize(stored.size());
        entity.setUploadedAt(LocalDateTime.now());
        RegleJeux saved = regleJeuxRepository.save(entity);
        if (previousKey != null && !previousKey.isBlank() && !previousKey.equals(stored.key())) {
            try {
                storageService.delete(previousKey);
            } catch (Exception ex) {
                log.warn("Ancien PDF règle de jeux non supprimé (id={}, key={}): {}",
                        id, previousKey, ex.getMessage());
            }
        }
        log.info("PDF règle de jeux remplacé — id={} file={}", id, original);
        return toRegleJeuxResponse(saved);
    }

    public void deleteRegleJeux(Long id) {
        RegleJeux entity = getRegleJeuxEntity(id);
        long links = regleJeuxRepository.countMasLinks(id);
        if (links > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Impossible de supprimer : " + links + " MAS utilisent cette règle de jeux");
        }
        String key = entity.getFileKey();
        regleJeuxRepository.delete(entity);
        if (key != null && !key.isBlank()) {
            try {
                storageService.delete(key);
            } catch (Exception ex) {
                log.warn("PDF règle de jeux non supprimé du stockage (id={}, key={}): {}",
                        id, key, ex.getMessage());
            }
        }
        log.info("Suppression en base — Règle de jeux id={} label={}", id, entity.getLabel());
    }

    public MasResponse create(MasRequest request) {
        Atelier atelier = atelierService.requireCurrentAtelier();
        String numero = request.getNumero().trim();
        ensureUniqueNumero(numero, atelier.getId(), null);
        Mas entity = Mas.builder()
                .numero(numero)
                .numeroSocle(trimToNull(request.getNumeroSocle()))
                .tauxRedistribution(normalizeTaux(request.getTauxRedistribution()))
                .dateMiseEnService(request.getDateMiseEnService())
                .typeMachine(trimToNull(request.getTypeMachine()))
                .numeroSerie(trimToNull(request.getNumeroSerie()))
                .dateCessation(request.getDateCessation())
                .destinationMachineUsagee(trimToNull(request.getDestinationMachineUsagee()))
                .marque(getMarque(request.getMarqueId()))
                .atelier(atelier)
                .build();
        applyDeno(entity, request);
        applyReglesJeux(entity, request.getRegleJeuxIds());
        entity.applyStatut(resolveStatut(request));
        applyIdentificationRules(entity, entity.getStatut());
        Mas saved = masRepository.save(entity);
        log.info("Création en base — MAS id={} numero={} marque={} atelier={}",
                saved.getId(),
                saved.getNumero(),
                saved.getMarque() != null ? saved.getMarque().getLabel() : null,
                atelier.getId());
        fitService.ensureFitSnapshotForMas(saved);
        atelierMemoirePublisher.publish("MAS_CREATED",
                "Nouvelle MAS « " + saved.getNumero() + " »"
                        + (saved.getMarque() != null ? " (" + saved.getMarque().getLabel() + ")" : ""));
        return toResponse(saved);
    }

    public MasResponse update(Long id, MasRequest request) {
        Mas entity = getEntity(id);
        String numero = request.getNumero().trim();
        ensureUniqueNumero(numero, entity.getAtelier().getId(), entity.getId());
        entity.setNumero(numero);
        entity.setNumeroSocle(trimToNull(request.getNumeroSocle()));
        entity.setTauxRedistribution(normalizeTaux(request.getTauxRedistribution()));
        entity.setDateMiseEnService(request.getDateMiseEnService());
        entity.setTypeMachine(trimToNull(request.getTypeMachine()));
        entity.setNumeroSerie(trimToNull(request.getNumeroSerie()));
        entity.setDateCessation(request.getDateCessation());
        entity.setDestinationMachineUsagee(trimToNull(request.getDestinationMachineUsagee()));
        entity.setMarque(getMarque(request.getMarqueId()));
        applyDeno(entity, request);
        applyReglesJeux(entity, request.getRegleJeuxIds());
        MasStatut statut = resolveStatut(request);
        entity.applyStatut(statut);
        applyIdentificationRules(entity, statut);
        Mas saved = masRepository.save(entity);
        log.info("Modification en base — MAS id={} numero={} marque={} atelier={}",
                saved.getId(),
                saved.getNumero(),
                saved.getMarque() != null ? saved.getMarque().getLabel() : null,
                saved.getAtelier() != null ? saved.getAtelier().getId() : null);
        fitService.syncEvolvingFieldsOnMasUpdate(saved);
        return toResponse(saved);
    }

    /**
     * Interdit : une MAS ne se supprime pas en base, elle change uniquement de statut.
     *
     * @param id identifiant (ignoré — refus systématique)
     */
    public void delete(Long id) {
        throw new ResponseStatusException(HttpStatus.METHOD_NOT_ALLOWED,
                "Une MAS ne peut pas être supprimée : modifiez son statut (ex. détruite, vendue, en réserve).");
    }

    /**
     * Associe (ou remplace) un bon de destruction PDF / image à une MAS détruite.
     */
    public MasResponse attachBonDestruction(Long id, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Sélectionnez un PDF ou une image du bon de destruction");
        }
        validateDestructionFile(file);

        Mas entity = getEntity(id);
        if (entity.getStatut() != MasStatut.DETRUITE) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Le bon de destruction ne peut être associé que si la MAS est détruite.");
        }

        String previousKey = entity.getDestructionFileKey();
        StorageService.StoredObject stored = storageService.store(file);
        String original = file.getOriginalFilename();
        if (original == null || original.isBlank()) {
            original = "bon-destruction";
        }
        if (original.length() > 255) {
            original = original.substring(0, 255);
        }

        entity.setDestructionFileKey(stored.key());
        entity.setDestructionFileUrl(stored.url());
        entity.setDestructionOriginalName(original);
        entity.setDestructionContentType(
                stored.contentType() != null ? stored.contentType() : file.getContentType());
        entity.setDestructionFileSize(stored.size());
        entity.setDestructionUploadedAt(LocalDateTime.now());
        Mas saved = masRepository.save(entity);

        if (previousKey != null && !previousKey.isBlank() && !previousKey.equals(stored.key())) {
            try {
                storageService.delete(previousKey);
            } catch (Exception ex) {
                log.warn("Ancien bon de destruction non supprimé (mas id={}, key={}): {}",
                        id, previousKey, ex.getMessage());
            }
        }

        log.info("Bon de destruction associé — MAS id={} file={}", id, original);
        return toResponse(saved);
    }

    public Mas getEntity(Long id) {
        Long atelierId = atelierService.requireCurrentAtelier().getId();
        return masRepository.findByIdAndAtelierId(id, atelierId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "MAS introuvable"));
    }

    private MarqueMas getMarque(Long id) {
        return marqueMasRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Marque MAS introuvable"));
    }

    private Deno resolveOptionalDeno(Long denoId) {
        if (denoId == null) {
            return null;
        }
        return denoRepository.findById(denoId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Dénomination introuvable"));
    }

    /**
     * Multi-déno : flag à true et aucune deno unique.
     * Sinon : dénomination optionnelle du référentiel.
     */
    private void applyReglesJeux(Mas entity, List<Long> regleJeuxIds) {
        if (regleJeuxIds == null || regleJeuxIds.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Sélectionnez au moins une règle de jeux");
        }
        Set<Long> uniqueIds = new LinkedHashSet<>(regleJeuxIds);
        List<RegleJeux> regles = regleJeuxRepository.findAllById(uniqueIds);
        if (regles.size() != uniqueIds.size()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Une ou plusieurs règles de jeux sont introuvables");
        }
        entity.getReglesJeux().clear();
        entity.getReglesJeux().addAll(regles);
    }

    private void applyDeno(Mas entity, MasRequest request) {
        boolean multi = Boolean.TRUE.equals(request.getMultiDeno());
        entity.setMultiDeno(multi);
        if (multi) {
            entity.setDeno(null);
            return;
        }
        entity.setDeno(resolveOptionalDeno(request.getDenoId()));
    }

    private void ensureUniqueNumero(String numero, Long atelierId, Long excludeId) {
        boolean exists = excludeId == null
                ? masRepository.existsByNumeroIgnoreCaseAndAtelierId(numero, atelierId)
                : masRepository.existsByNumeroIgnoreCaseAndAtelierIdAndIdNot(numero, atelierId, excludeId);
        if (exists) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Numéro MAS déjà utilisé dans cet atelier");
        }
    }

    private MasResponse toResponse(Mas entity) {
        MarqueMas marque = entity.getMarque();
        Deno deno = entity.getDeno();
        List<RegleJeuxResponse> regles = entity.getReglesJeux() == null
                ? List.of()
                : entity.getReglesJeux().stream()
                        .sorted(Comparator.comparing(RegleJeux::getLabel, String.CASE_INSENSITIVE_ORDER))
                        .map(this::toRegleJeuxResponse)
                        .toList();
        return MasResponse.builder()
                .id(entity.getId())
                .numero(entity.getNumero())
                .numeroSocle(entity.getNumeroSocle())
                .tauxRedistribution(entity.getTauxRedistribution())
                .dateMiseEnService(entity.getDateMiseEnService())
                .typeMachine(entity.getTypeMachine())
                .numeroSerie(entity.getNumeroSerie())
                .dateCessation(entity.getDateCessation())
                .destinationMachineUsagee(entity.getDestinationMachineUsagee())
                .destructionFileUrl(storageService.resolveAccessUrl(
                        entity.getDestructionFileKey(),
                        entity.getDestructionFileUrl(),
                        StorageService.AccessKind.DOCUMENT))
                .destructionOriginalName(entity.getDestructionOriginalName())
                .destructionContentType(entity.getDestructionContentType())
                .destructionFileSize(entity.getDestructionFileSize())
                .destructionUploadedAt(entity.getDestructionUploadedAt())
                .marqueId(marque != null ? marque.getId() : null)
                .marque(marque != null ? marque.getCode() : null)
                .marqueLabel(marque != null ? marque.getLabel() : null)
                .multiDeno(entity.isMultiDeno())
                .denoId(entity.isMultiDeno() || deno == null ? null : deno.getId())
                .denoValeur(entity.isMultiDeno() || deno == null ? null : deno.getValeur())
                .denoLabel(entity.isMultiDeno()
                        ? MULTI_DENO_LABEL
                        : (deno != null ? deno.getLabel() : null))
                .statut(entity.getStatut() != null ? entity.getStatut().name() : MasStatut.UTILISEE.name())
                .statutLabel(entity.getStatut() != null ? entity.getStatut().label() : MasStatut.UTILISEE.label())
                .utilise(entity.isUtilise())
                .regleJeuxIds(regles.stream().map(RegleJeuxResponse::getId).collect(Collectors.toList()))
                .reglesJeux(regles)
                .build();
    }

    private MasStatut resolveStatut(MasRequest request) {
        if (request.getStatut() != null && !request.getStatut().isBlank()) {
            try {
                return MasStatut.valueOf(request.getStatut().trim().toUpperCase());
            } catch (IllegalArgumentException ex) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Statut MAS invalide (UTILISEE, EN_RESERVE, VENDUE, DETRUITE)");
            }
        }
        if (request.getUtilise() != null) {
            return MasStatut.fromUtilise(Boolean.TRUE.equals(request.getUtilise()));
        }
        return MasStatut.UTILISEE;
    }

    /**
     * Date de cessation : Vendue / En réserve / Détruite.
     * Destination machine usagée : Vendue uniquement.
     * Bon de destruction : uniquement si Détruite.
     */
    private void applyIdentificationRules(Mas entity, MasStatut statut) {
        if (statut == null || statut == MasStatut.UTILISEE) {
            entity.setDateCessation(null);
            entity.setDestinationMachineUsagee(null);
            clearDestructionStorage(entity);
            return;
        }
        if (statut != MasStatut.VENDUE) {
            entity.setDestinationMachineUsagee(null);
        }
        if (statut != MasStatut.DETRUITE) {
            clearDestructionStorage(entity);
        }
    }

    private void clearDestructionStorage(Mas entity) {
        String key = entity.getDestructionFileKey();
        if (key != null && !key.isBlank()) {
            try {
                storageService.delete(key);
            } catch (Exception ex) {
                log.warn("Bon de destruction non supprimé du stockage (mas id={}, key={}): {}",
                        entity.getId(), key, ex.getMessage());
            }
        }
        entity.setDestructionFileKey(null);
        entity.setDestructionFileUrl(null);
        entity.setDestructionOriginalName(null);
        entity.setDestructionContentType(null);
        entity.setDestructionFileSize(null);
        entity.setDestructionUploadedAt(null);
    }

    private void validateDestructionFile(MultipartFile file) {
        DocumentUploadValidator.validatePdfOrImage(file, "bon de destruction");
    }

    private MarqueMasResponse toMarqueResponse(MarqueMas marque) {
        return MarqueMasResponse.builder()
                .id(marque.getId())
                .code(marque.getCode())
                .label(marque.getLabel())
                .value(marque.getId())
                .build();
    }

    private DenoResponse toDenoResponse(Deno deno) {
        return DenoResponse.builder()
                .id(deno.getId())
                .valeur(deno.getValeur())
                .label(deno.getLabel())
                .value(deno.getId())
                .build();
    }

    private RegleJeux getRegleJeuxEntity(Long id) {
        return regleJeuxRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Règle de jeux introuvable"));
    }

    private RegleJeuxResponse toRegleJeuxResponse(RegleJeux regle) {
        return RegleJeuxResponse.builder()
                .id(regle.getId())
                .code(regle.getCode())
                .label(regle.getLabel())
                .description(regle.getDescription())
                .fileUrl(storageService.resolveAccessUrl(
                        regle.getFileKey(), regle.getFileUrl(), StorageService.AccessKind.DOCUMENT))
                .originalName(regle.getOriginalName())
                .contentType(regle.getContentType())
                .fileSize(regle.getFileSize())
                .uploadedAt(regle.getUploadedAt())
                .value(regle.getId())
                .masCount(regleJeuxRepository.countMasLinks(regle.getId()))
                .build();
    }

    private RegleJeuxResponse toRegleJeuxResponseDetailed(RegleJeux regle, Long atelierId) {
        List<Mas> linked = masRepository.findAllByRegleJeuxIdAndAtelierId(regle.getId(), atelierId);
        List<RegleJeuxMasSummary> masses = linked.stream()
                .map(m -> RegleJeuxMasSummary.builder()
                        .id(m.getId())
                        .numero(m.getNumero())
                        .marqueLabel(m.getMarque() != null ? m.getMarque().getLabel() : null)
                        .build())
                .toList();
        List<Long> masIds = masses.stream().map(RegleJeuxMasSummary::getId).toList();
        return RegleJeuxResponse.builder()
                .id(regle.getId())
                .code(regle.getCode())
                .label(regle.getLabel())
                .description(regle.getDescription())
                .fileUrl(storageService.resolveAccessUrl(
                        regle.getFileKey(), regle.getFileUrl(), StorageService.AccessKind.DOCUMENT))
                .originalName(regle.getOriginalName())
                .contentType(regle.getContentType())
                .fileSize(regle.getFileSize())
                .uploadedAt(regle.getUploadedAt())
                .value(regle.getId())
                .masCount(masIds.size())
                .masIds(masIds)
                .masses(masses)
                .build();
    }

    private static String normalizeOriginalFilename(String raw, String fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        String trimmed = raw.trim();
        return trimmed.length() > 255 ? trimmed.substring(0, 255) : trimmed;
    }

    static String toCode(String label) {
        String normalized = Normalizer.normalize(label, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .replaceAll("[^A-Za-z0-9]+", "_")
                .replaceAll("^_|_$", "")
                .toUpperCase(Locale.ROOT);
        if (normalized.isBlank()) {
            return "MARQUE";
        }
        return normalized.length() > 60 ? normalized.substring(0, 60) : normalized;
    }

    static String formatDenoLabel(BigDecimal valeur) {
        DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.FRANCE);
        DecimalFormat df = new DecimalFormat("0.00", symbols);
        df.setMinimumFractionDigits(2);
        df.setMaximumFractionDigits(4);
        return df.format(valeur) + " €";
    }

    private static BigDecimal normalizeTaux(BigDecimal taux) {
        if (taux == null) {
            return null;
        }
        return taux.setScale(2, RoundingMode.HALF_UP);
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
