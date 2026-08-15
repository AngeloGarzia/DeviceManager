package com.devicemanager.service;

import com.devicemanager.dto.DeviceDocumentResponse;
import com.devicemanager.dto.DevicePhotoResponse;
import com.devicemanager.dto.DeviceRequest;
import com.devicemanager.dto.DeviceResponse;
import com.devicemanager.entity.Device;
import com.devicemanager.entity.DeviceDocument;
import com.devicemanager.entity.DevicePhoto;
import com.devicemanager.entity.MarqueMas;
import com.devicemanager.entity.Mas;
import com.devicemanager.entity.Sfm;
import com.devicemanager.entity.User;
import com.devicemanager.repository.DeviceRepository;
import com.devicemanager.repository.UserRepository;
import com.devicemanager.security.DeviceDocumentTypes;
import com.devicemanager.security.FileMagicBytesValidator;
import com.devicemanager.security.StockMouvementSources;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.Hibernate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Service métier du catalogue de pièces détachées (devices).
 * <p>
 * CRUD, recherche multi-termes, photos et liens MAS/SFM, scopé strictement
 * à l'atelier courant ({@code X-Atelier-Id}).
 */
@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class DeviceService {

    public static final int MAX_PHOTOS = 5;
    public static final int MAX_DOCUMENTS = 3;

    private final DeviceRepository deviceRepository;
    private final SfmService sfmService;
    private final MasService masService;
    private final StorageService storageService;
    private final ImageOptimizationService imageOptimizationService;
    private final AtelierService atelierService;
    private final StockMouvementService stockMouvementService;
    private final UserRepository userRepository;

    /**
     * Liste ou recherche les pièces de l'atelier courant.
     *
     * @param q termes de recherche optionnels (tous les mots doivent correspondre)
     * @return fiches pièces avec photos
     * @throws org.springframework.web.server.ResponseStatusException {@code 400} si atelier non sélectionné
     */
    @Transactional(readOnly = true)
    public List<DeviceResponse> findAll(String q) {
        Long atelierId = atelierService.requireCurrentAtelier().getId();
        List<Device> list;
        if (q == null || q.isBlank()) {
            list = deviceRepository.findAllWithRelations(atelierId);
        } else {
            String[] tokens = java.util.Arrays.stream(q.trim().split("\\s+"))
                    .map(String::trim)
                    .filter(t -> !t.isEmpty())
                    .toArray(String[]::new);
            list = deviceRepository.search(atelierId, tokens[0]);
            if (tokens.length > 1) {
                list = list.stream()
                        .filter(d -> matchesAllSearchTokens(d, tokens))
                        .toList();
            }
        }
        list.forEach(d -> Hibernate.initialize(d.getPhotos()));
        return list.stream().map(this::toResponse).toList();
    }

    /** Chaque mot doit apparaître dans nom / référence / usage / SFM / MAS / marque. */
    private boolean matchesAllSearchTokens(Device d, String[] tokens) {
        String haystack = String.join(" ",
                nullToEmpty(d.getNom()),
                nullToEmpty(d.getReference()),
                nullToEmpty(d.getUsage()),
                d.getSfm() != null ? nullToEmpty(d.getSfm().getNom()) : "",
                d.getMas() != null ? nullToEmpty(d.getMas().getNumero()) : "",
                d.getMarque() != null ? nullToEmpty(d.getMarque().getLabel()) : "",
                d.getMarque() != null ? nullToEmpty(d.getMarque().getCode()) : "",
                d.getMas() != null && d.getMas().getMarque() != null
                        ? nullToEmpty(d.getMas().getMarque().getLabel()) : "",
                d.getMas() != null && d.getMas().getMarque() != null
                        ? nullToEmpty(d.getMas().getMarque().getCode()) : ""
        ).toLowerCase();
        for (String token : tokens) {
            if (!haystack.contains(token.toLowerCase())) {
                return false;
            }
        }
        return true;
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    /**
     * Retourne une pièce par identifiant dans l'atelier courant.
     *
     * @param id identifiant de la pièce
     * @return fiche complète
     * @throws org.springframework.web.server.ResponseStatusException {@code 404} si introuvable
     */
    @Transactional(readOnly = true)
    public DeviceResponse findById(Long id) {
        return toResponse(getEntity(id));
    }

    /**
     * Crée une pièce avec photos et documents PDF optionnels dans l'atelier courant.
     *
     * @param request   métadonnées (nom, usage, MAS/SFM, types des nouveaux PDF, etc.)
     * @param photos    images (1 à {@link #MAX_PHOTOS})
     * @param documents PDF (manuel / datasheet / notice), types dans {@code request.newDocumentTypes}
     * @return pièce persistée
     */
    public DeviceResponse create(
            DeviceRequest request,
            List<MultipartFile> photos,
            List<MultipartFile> documents) {
        List<MultipartFile> files = normalizeFiles(photos);
        ensurePhotoCount(files.size(), true);
        files.forEach(this::validateImageFile);

        List<MultipartFile> docs = normalizeFiles(documents);
        List<String> docTypes = normalizeDocumentTypes(request.getNewDocumentTypes(), docs.size());

        var atelier = atelierService.requireCurrentAtelier();
        String nom = request.getNom().trim();
        String reference = normalizeOptionalText(request.getReference());
        ensureUniqueNom(nom, atelier.getId(), null);
        ensureUniqueReference(reference, atelier.getId(), null);
        Sfm sfm = resolveOptionalSfm(request.getSfmId());
        Mas mas = resolveOptionalMas(request.getMasId());
        if (sfm != null && mas != null) {
            ensureSfmCoversMas(sfm, mas);
        }
        MarqueMas marque = mas != null ? inheritMarqueFromMas(mas) : null;

        Device entity = Device.builder()
                .nom(nom)
                .reference(reference)
                .usage(request.getUsage().trim())
                .informationTechnique(normalizeOptionalText(request.getInformationTechnique()))
                .dateAcquisition(request.getDateAcquisition())
                .obsolete(Boolean.TRUE.equals(request.getObsolete()))
                .stock(normalizeStock(request.getStock()))
                .photos(new ArrayList<>())
                .documents(new ArrayList<>())
                .sfm(sfm)
                .mas(mas)
                .marque(marque)
                .atelier(atelier)
                .build();

        addNewPhotos(entity, files);
        syncPrimaryPhoto(entity);
        addNewDocuments(entity, docs, docTypes);

        Device saved = deviceRepository.save(entity);
        log.info("Création en base — Pièce id={} nom={} référence={} photos={} docs={} atelier={}",
                saved.getId(), saved.getNom(), saved.getReference(),
                saved.getPhotos().size(), saved.getDocuments().size(), atelier.getId());
        return toResponse(saved);
    }

    /**
     * Met à jour uniquement la quantité en stock d'une pièce.
     *
     * @param id identifiant de la pièce
     * @param stock quantité (≥ 0)
     * @param username acteur authentifié (journal de stock)
     * @return pièce mise à jour
     * @throws org.springframework.web.server.ResponseStatusException {@code 404} si introuvable
     */
    public DeviceResponse updateStock(Long id, Integer stock, String username) {
        Device entity = getEntity(id);
        int stockAvant = Math.max(0, entity.getStock());
        int stockApres = normalizeStock(stock);
        entity.setStock(stockApres);
        Device saved = deviceRepository.save(entity);
        if (stockApres != stockAvant) {
            String acteurNom = userRepository.findByUsername(username)
                    .map(this::displayUserName)
                    .orElse(username);
            stockMouvementService.record(
                    atelierService.requireCurrentAtelier(),
                    saved,
                    stockAvant,
                    stockApres,
                    StockMouvementSources.MANUAL,
                    saved.getId(),
                    acteurNom);
        }
        log.info("Mise à jour stock — Pièce id={} stock={} (avant={}) par={}",
                saved.getId(), saved.getStock(), stockAvant, username);
        return toResponse(saved);
    }

    private String displayUserName(User user) {
        String prenom = user.getPrenom() == null ? "" : user.getPrenom().trim();
        String nom = user.getNom() == null ? "" : user.getNom().trim();
        String full = (prenom + " " + nom).trim();
        return full.isEmpty() ? user.getUsername() : full;
    }

    /**
     * Met à jour une pièce, ses photos et ses documents PDF.
     *
     * @param id         identifiant de la pièce
     * @param request    métadonnées ({@code keepPhotoIds}, {@code keepDocumentIds}, {@code newDocumentTypes})
     * @param photos     nouvelles images
     * @param documents  nouveaux PDF
     * @return pièce modifiée
     */
    public DeviceResponse update(
            Long id,
            DeviceRequest request,
            List<MultipartFile> photos,
            List<MultipartFile> documents) {
        Device entity = getEntity(id);
        String nom = request.getNom().trim();
        String reference = normalizeOptionalText(request.getReference());
        ensureUniqueNom(nom, entity.getAtelier().getId(), entity.getId());
        ensureUniqueReference(reference, entity.getAtelier().getId(), entity.getId());
        entity.setNom(nom);
        entity.setReference(reference);
        entity.setUsage(request.getUsage().trim());
        entity.setInformationTechnique(normalizeOptionalText(request.getInformationTechnique()));
        entity.setDateAcquisition(request.getDateAcquisition());
        entity.setObsolete(Boolean.TRUE.equals(request.getObsolete()));
        entity.setStock(normalizeStock(request.getStock()));
        Sfm sfm = resolveOptionalSfm(request.getSfmId());
        Mas mas = resolveOptionalMas(request.getMasId());
        if (sfm != null && mas != null) {
            ensureSfmCoversMas(sfm, mas);
        }
        entity.setSfm(sfm);
        entity.setMas(mas);
        entity.setMarque(mas != null ? inheritMarqueFromMas(mas) : null);

        List<MultipartFile> newFiles = normalizeFiles(photos);
        newFiles.forEach(this::validateImageFile);

        List<Long> keepOrder = request.getKeepPhotoIds() == null
                ? List.of()
                : request.getKeepPhotoIds().stream().filter(Objects::nonNull).toList();
        Set<Long> keepIds = new HashSet<>(keepOrder);

        Map<Long, DevicePhoto> byId = entity.getPhotos().stream()
                .filter(p -> p.getId() != null)
                .collect(Collectors.toMap(DevicePhoto::getId, p -> p, (a, b) -> a));

        List<DevicePhoto> kept = new ArrayList<>();
        for (Long keepId : keepOrder) {
            DevicePhoto photo = byId.get(keepId);
            if (photo != null) {
                kept.add(photo);
            }
        }

        int total = kept.size() + newFiles.size();
        ensurePhotoCount(total, true);

        for (DevicePhoto photo : List.copyOf(entity.getPhotos())) {
            if (!keepIds.contains(photo.getId())) {
                if (photo.getPhotoKey() != null) {
                    storageService.delete(photo.getPhotoKey());
                }
                entity.getPhotos().remove(photo);
            }
        }

        entity.getPhotos().clear();
        int position = 0;
        for (DevicePhoto photo : kept) {
            photo.setPosition(position++);
            photo.setDevice(entity);
            entity.getPhotos().add(photo);
        }
        addNewPhotos(entity, newFiles);
        syncPrimaryPhoto(entity);

        applyDocumentUpdates(entity, request, documents);

        Device saved = deviceRepository.save(entity);
        log.info("Modification en base — Pièce id={} nom={} référence={} photos={} docs={} atelier={}",
                saved.getId(),
                saved.getNom(),
                saved.getReference(),
                saved.getPhotos().size(),
                saved.getDocuments() == null ? 0 : saved.getDocuments().size(),
                saved.getAtelier() != null ? saved.getAtelier().getId() : null);
        return toResponse(saved);
    }

    /**
     * Supprime une pièce et les fichiers stockés associés.
     *
     * @param id identifiant de la pièce
     * @throws org.springframework.web.server.ResponseStatusException {@code 404} si introuvable
     */
    public void delete(Long id) {
        Device entity = getEntity(id);
        Long atelierId = entity.getAtelier() != null ? entity.getAtelier().getId() : null;
        String nom = entity.getNom();
        String reference = entity.getReference();
        for (DevicePhoto photo : entity.getPhotos()) {
            if (photo.getPhotoKey() != null) {
                storageService.delete(photo.getPhotoKey());
            }
        }
        if (entity.getDocuments() != null) {
            for (DeviceDocument doc : entity.getDocuments()) {
                if (doc.getFileKey() != null) {
                    storageService.delete(doc.getFileKey());
                }
            }
        }
        if (entity.getPhotoKey() != null
                && entity.getPhotos().stream().noneMatch(p -> Objects.equals(p.getPhotoKey(), entity.getPhotoKey()))) {
            storageService.delete(entity.getPhotoKey());
        }
        deviceRepository.delete(entity);
        log.info("Suppression en base — Pièce id={} nom={} référence={} atelier={}",
                id, nom, reference, atelierId);
    }

    private void applyDocumentUpdates(
            Device entity,
            DeviceRequest request,
            List<MultipartFile> documents) {
        if (entity.getDocuments() == null) {
            entity.setDocuments(new ArrayList<>());
        }
        List<MultipartFile> docs = normalizeFiles(documents);
        List<String> docTypes = normalizeDocumentTypes(request.getNewDocumentTypes(), docs.size());

        List<Long> keepOrder = request.getKeepDocumentIds() == null
                ? List.of()
                : request.getKeepDocumentIds().stream().filter(Objects::nonNull).toList();
        Set<Long> keepIds = new HashSet<>(keepOrder);

        Map<Long, DeviceDocument> byId = entity.getDocuments().stream()
                .filter(d -> d.getId() != null)
                .collect(Collectors.toMap(DeviceDocument::getId, d -> d, (a, b) -> a));

        List<DeviceDocument> kept = new ArrayList<>();
        for (Long keepId : keepOrder) {
            DeviceDocument doc = byId.get(keepId);
            if (doc != null) {
                kept.add(doc);
            }
        }

        for (DeviceDocument doc : List.copyOf(entity.getDocuments())) {
            if (!keepIds.contains(doc.getId())) {
                if (doc.getFileKey() != null) {
                    storageService.delete(doc.getFileKey());
                }
                entity.getDocuments().remove(doc);
            }
        }

        entity.getDocuments().clear();
        for (DeviceDocument doc : kept) {
            doc.setDevice(entity);
            entity.getDocuments().add(doc);
        }
        addNewDocuments(entity, docs, docTypes);
    }

    private void addNewDocuments(Device entity, List<MultipartFile> files, List<String> types) {
        if (entity.getDocuments() == null) {
            entity.setDocuments(new ArrayList<>());
        }
        Set<String> usedTypes = entity.getDocuments().stream()
                .map(DeviceDocument::getDocType)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(HashSet::new));

        for (int i = 0; i < files.size(); i++) {
            MultipartFile file = files.get(i);
            String type = types.get(i);
            if (usedTypes.contains(type)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Un document « " + typeLabel(type) + " » est déjà présent pour cette pièce");
            }
            validatePdfFile(file);
            StorageService.StoredObject stored = storageService.store(file);
            String original = file.getOriginalFilename();
            if (original == null || original.isBlank()) {
                original = type.toLowerCase() + ".pdf";
            }
            DeviceDocument doc = DeviceDocument.builder()
                    .device(entity)
                    .docType(type)
                    .fileKey(stored.key())
                    .fileUrl(stored.url())
                    .originalName(original.length() > 255 ? original.substring(0, 255) : original)
                    .contentType(stored.contentType() != null ? stored.contentType() : "application/pdf")
                    .fileSize(stored.size())
                    .build();
            entity.getDocuments().add(doc);
            usedTypes.add(type);
        }
        if (entity.getDocuments().size() > MAX_DOCUMENTS) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Maximum " + MAX_DOCUMENTS + " documents PDF par pièce (manuel, datasheet, notice)");
        }
    }

    private List<String> normalizeDocumentTypes(List<String> rawTypes, int fileCount) {
        List<String> types = rawTypes == null ? List.of() : rawTypes.stream()
                .map(DeviceDocumentTypes::normalize)
                .toList();
        if (fileCount == 0) {
            return List.of();
        }
        if (types.size() != fileCount || types.stream().anyMatch(Objects::isNull)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Chaque PDF doit avoir un type : MANUAL, DATASHEET ou NOTICE");
        }
        Set<String> unique = new HashSet<>();
        for (String t : types) {
            if (!unique.add(t)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Deux PDF du même type (« " + typeLabel(t) + " ») dans le même envoi");
            }
        }
        return types;
    }

    private void validatePdfFile(MultipartFile file) {
        String contentType = file.getContentType();
        String name = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase();
        boolean pdfType = contentType != null && (
                contentType.equalsIgnoreCase("application/pdf")
                        || contentType.equalsIgnoreCase("application/x-pdf"));
        boolean pdfName = name.endsWith(".pdf");
        if (!pdfType && !pdfName) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Le document doit être un fichier PDF");
        }
        try {
            FileMagicBytesValidator.validatePdfMagicBytes(file.getBytes());
        } catch (ResponseStatusException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Document PDF illisible");
        }
    }

    private static String typeLabel(String type) {
        return switch (type) {
            case DeviceDocumentTypes.MANUAL -> "manuel";
            case DeviceDocumentTypes.DATASHEET -> "datasheet";
            case DeviceDocumentTypes.NOTICE -> "notice";
            default -> type;
        };
    }

    private void addNewPhotos(Device entity, List<MultipartFile> files) {
        int position = entity.getPhotos().size();
        for (MultipartFile file : files) {
            MultipartFile optimized = imageOptimizationService.optimize(file);
            StorageService.StoredObject stored = storageService.store(optimized);
            DevicePhoto photo = DevicePhoto.builder()
                    .device(entity)
                    .photoKey(stored.key())
                    .photoUrl(stored.url())
                    .contentType(stored.contentType())
                    .fileSize(stored.size())
                    .position(position++)
                    .build();
            entity.getPhotos().add(photo);
        }
    }

    private void syncPrimaryPhoto(Device entity) {
        if (entity.getPhotos() == null || entity.getPhotos().isEmpty()) {
            entity.setPhotoKey(null);
            entity.setPhotoUrl(null);
            entity.setContentType(null);
            entity.setFileSize(null);
            return;
        }
        DevicePhoto first = entity.getPhotos().getFirst();
        entity.setPhotoKey(first.getPhotoKey());
        entity.setPhotoUrl(first.getPhotoUrl());
        entity.setContentType(first.getContentType());
        entity.setFileSize(first.getFileSize());
    }

    private List<MultipartFile> normalizeFiles(List<MultipartFile> photos) {
        if (photos == null) {
            return List.of();
        }
        return photos.stream()
                .filter(Objects::nonNull)
                .filter(f -> !f.isEmpty())
                .toList();
    }

    private void ensurePhotoCount(int count, boolean requireAtLeastOne) {
        if (requireAtLeastOne && count < 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ajoutez au moins une photo de la pièce");
        }
        if (count > MAX_PHOTOS) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Maximum " + MAX_PHOTOS + " photos par pièce détachée");
        }
    }

    private void validateImageFile(MultipartFile photo) {
        String contentType = photo.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Le fichier doit être une photo (JPEG, PNG…)");
        }
    }

    private Device getEntity(Long id) {
        Long atelierId = atelierService.requireCurrentAtelier().getId();
        Device entity = deviceRepository.findByIdWithRelations(id, atelierId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Pièce détachée introuvable"));
        Hibernate.initialize(entity.getPhotos());
        Hibernate.initialize(entity.getDocuments());
        return entity;
    }

    private void ensureUniqueNom(String nom, Long atelierId, Long excludeId) {
        boolean exists = excludeId == null
                ? deviceRepository.existsByNomIgnoreCaseAndAtelierId(nom, atelierId)
                : deviceRepository.existsByNomIgnoreCaseAndAtelierIdAndIdNot(nom, atelierId, excludeId);
        if (exists) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Nom de pièce déjà utilisé dans cet atelier");
        }
    }

    private void ensureUniqueReference(String reference, Long atelierId, Long excludeId) {
        if (reference == null || reference.isBlank()) {
            return;
        }
        boolean exists = excludeId == null
                ? deviceRepository.existsByReferenceIgnoreCaseAndAtelierId(reference, atelierId)
                : deviceRepository.existsByReferenceIgnoreCaseAndAtelierIdAndIdNot(reference, atelierId, excludeId);
        if (exists) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Référence déjà utilisée dans cet atelier");
        }
    }

    private String normalizeOptionalText(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private Sfm resolveOptionalSfm(Long sfmId) {
        if (sfmId == null) {
            return null;
        }
        return sfmService.getEntity(sfmId);
    }

    private Mas resolveOptionalMas(Long masId) {
        if (masId == null) {
            return null;
        }
        return masService.getEntity(masId);
    }

    private MarqueMas inheritMarqueFromMas(Mas mas) {
        if (mas.getMarque() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La MAS sélectionnée n'a pas de marque");
        }
        return mas.getMarque();
    }

    private void ensureSfmCoversMas(Sfm sfm, Mas mas) {
        if (mas.getMarque() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La MAS sélectionnée n'a pas de marque");
        }
        if (sfm.getMarques() == null || sfm.getMarques().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Le SFM n'a aucune marque associée");
        }
        Long marqueId = mas.getMarque().getId();
        boolean covered = sfm.getMarques().stream()
                .anyMatch(m -> m.getId().equals(marqueId));
        if (!covered) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "La MAS n'appartient pas aux marques couvertes par ce SFM");
        }
    }

    private static int normalizeStock(Integer stock) {
        if (stock == null) {
            return 0;
        }
        return Math.max(0, stock);
    }

    private DeviceResponse toResponse(Device entity) {
        Mas mas = entity.getMas();
        MarqueMas marque = entity.getMarque();
        if (marque == null && mas != null) {
            marque = mas.getMarque();
        }
        String marqueLabel = marque != null ? marque.getLabel() : null;

        List<DevicePhotoResponse> photos = entity.getPhotos() == null
                ? List.of()
                : entity.getPhotos().stream()
                .map(p -> DevicePhotoResponse.builder()
                        .id(p.getId())
                        .photoUrl(p.getPhotoUrl())
                        .contentType(p.getContentType())
                        .fileSize(p.getFileSize())
                        .position(p.getPosition())
                        .build())
                .toList();

        List<DeviceDocumentResponse> documents = entity.getDocuments() == null
                ? List.of()
                : entity.getDocuments().stream()
                .map(d -> DeviceDocumentResponse.builder()
                        .id(d.getId())
                        .docType(d.getDocType())
                        .fileUrl(d.getFileUrl())
                        .originalName(d.getOriginalName())
                        .contentType(d.getContentType())
                        .fileSize(d.getFileSize())
                        .build())
                .toList();

        String primaryUrl = entity.getPhotoUrl();
        if ((primaryUrl == null || primaryUrl.isBlank()) && !photos.isEmpty()) {
            primaryUrl = photos.getFirst().getPhotoUrl();
        }

        return DeviceResponse.builder()
                .id(entity.getId())
                .nom(entity.getNom())
                .reference(entity.getReference())
                .usage(entity.getUsage())
                .informationTechnique(entity.getInformationTechnique())
                .dateAcquisition(entity.getDateAcquisition())
                .obsolete(entity.isObsolete())
                .stock(entity.getStock())
                .photoUrl(primaryUrl)
                .contentType(entity.getContentType())
                .fileSize(entity.getFileSize())
                .photos(photos)
                .documents(documents)
                .sfmId(entity.getSfm() != null ? entity.getSfm().getId() : null)
                .sfmNom(entity.getSfm() != null ? entity.getSfm().getNom() : null)
                .masId(mas != null ? mas.getId() : null)
                .masNumero(mas != null ? mas.getNumero() : null)
                .masMarque(mas != null && mas.getMarque() != null ? mas.getMarque().getLabel() : marqueLabel)
                .marqueId(marque != null ? marque.getId() : null)
                .marque(marque != null ? marque.getCode() : null)
                .marqueLabel(marqueLabel)
                .build();
    }
}
