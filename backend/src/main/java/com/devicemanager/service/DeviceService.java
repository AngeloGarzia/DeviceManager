package com.devicemanager.service;

import com.devicemanager.dto.DevicePhotoResponse;
import com.devicemanager.dto.DeviceRequest;
import com.devicemanager.dto.DeviceResponse;
import com.devicemanager.entity.Device;
import com.devicemanager.entity.DevicePhoto;
import com.devicemanager.entity.MarqueMas;
import com.devicemanager.entity.Mas;
import com.devicemanager.entity.Sfm;
import com.devicemanager.repository.DeviceRepository;
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

    private final DeviceRepository deviceRepository;
    private final SfmService sfmService;
    private final MasService masService;
    private final StorageService storageService;
    private final ImageOptimizationService imageOptimizationService;
    private final AtelierService atelierService;

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
     * Crée une pièce avec photos dans l'atelier courant.
     *
     * @param request métadonnées (nom, usage, MAS/SFM, etc.)
     * @param photos images (1 à {@link #MAX_PHOTOS})
     * @return pièce persistée
     * @throws org.springframework.web.server.ResponseStatusException {@code 400} si validation échoue ;
     *         {@code 409} en cas de doublon nom/référence
     */
    public DeviceResponse create(DeviceRequest request, List<MultipartFile> photos) {
        List<MultipartFile> files = normalizeFiles(photos);
        ensurePhotoCount(files.size(), true);
        files.forEach(this::validateImageFile);

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
                .dateAcquisition(request.getDateAcquisition())
                .obsolete(Boolean.TRUE.equals(request.getObsolete()))
                .stock(normalizeStock(request.getStock()))
                .photos(new ArrayList<>())
                .sfm(sfm)
                .mas(mas)
                .marque(marque)
                .atelier(atelier)
                .build();

        addNewPhotos(entity, files);
        syncPrimaryPhoto(entity);

        Device saved = deviceRepository.save(entity);
        log.info("Création en base — Pièce id={} nom={} référence={} photos={} atelier={}",
                saved.getId(), saved.getNom(), saved.getReference(), saved.getPhotos().size(), atelier.getId());
        return toResponse(saved);
    }

    /**
     * Met à jour uniquement la quantité en stock d'une pièce.
     *
     * @param id identifiant de la pièce
     * @param stock quantité (≥ 0)
     * @return pièce mise à jour
     * @throws org.springframework.web.server.ResponseStatusException {@code 404} si introuvable
     */
    public DeviceResponse updateStock(Long id, Integer stock) {
        Device entity = getEntity(id);
        entity.setStock(normalizeStock(stock));
        Device saved = deviceRepository.save(entity);
        log.info("Mise à jour stock — Pièce id={} stock={}", saved.getId(), saved.getStock());
        return toResponse(saved);
    }

    /**
     * Met à jour une pièce et remplace éventuellement ses photos.
     *
     * @param id identifiant de la pièce
     * @param request métadonnées mises à jour (incluant {@code keepPhotoIds})
     * @param photos nouvelles images à ajouter
     * @return pièce modifiée
     * @throws org.springframework.web.server.ResponseStatusException {@code 404} si introuvable ;
     *         {@code 409} en cas de conflit nom/référence
     */
    public DeviceResponse update(Long id, DeviceRequest request, List<MultipartFile> photos) {
        Device entity = getEntity(id);
        String nom = request.getNom().trim();
        String reference = normalizeOptionalText(request.getReference());
        ensureUniqueNom(nom, entity.getAtelier().getId(), entity.getId());
        ensureUniqueReference(reference, entity.getAtelier().getId(), entity.getId());
        entity.setNom(nom);
        entity.setReference(reference);
        entity.setUsage(request.getUsage().trim());
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

        Device saved = deviceRepository.save(entity);
        log.info("Modification en base — Pièce id={} nom={} référence={} photos={} atelier={}",
                saved.getId(),
                saved.getNom(),
                saved.getReference(),
                saved.getPhotos().size(),
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
        if (entity.getPhotoKey() != null
                && entity.getPhotos().stream().noneMatch(p -> Objects.equals(p.getPhotoKey(), entity.getPhotoKey()))) {
            storageService.delete(entity.getPhotoKey());
        }
        deviceRepository.delete(entity);
        log.info("Suppression en base — Pièce id={} nom={} référence={} atelier={}",
                id, nom, reference, atelierId);
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

        String primaryUrl = entity.getPhotoUrl();
        if ((primaryUrl == null || primaryUrl.isBlank()) && !photos.isEmpty()) {
            primaryUrl = photos.getFirst().getPhotoUrl();
        }

        return DeviceResponse.builder()
                .id(entity.getId())
                .nom(entity.getNom())
                .reference(entity.getReference())
                .usage(entity.getUsage())
                .dateAcquisition(entity.getDateAcquisition())
                .obsolete(entity.isObsolete())
                .stock(entity.getStock())
                .photoUrl(primaryUrl)
                .contentType(entity.getContentType())
                .fileSize(entity.getFileSize())
                .photos(photos)
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
