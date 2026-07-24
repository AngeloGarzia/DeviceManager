package com.devicemanager.service;

import com.devicemanager.dto.DeviceRequest;
import com.devicemanager.dto.DeviceResponse;
import com.devicemanager.entity.Device;
import com.devicemanager.entity.MarqueMas;
import com.devicemanager.entity.Mas;
import com.devicemanager.entity.Sfm;
import com.devicemanager.repository.DeviceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class DeviceService {

    private final DeviceRepository deviceRepository;
    private final SfmService sfmService;
    private final MasService masService;
    private final StorageService storageService;
    private final ImageOptimizationService imageOptimizationService;
    private final AtelierService atelierService;

    @Transactional(readOnly = true)
    public List<DeviceResponse> findAll(String q) {
        Long atelierId = atelierService.requireCurrentAtelier().getId();
        List<Device> list = (q == null || q.isBlank())
                ? deviceRepository.findAllWithRelations(atelierId)
                : deviceRepository.search(atelierId, q.trim());
        return list.stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public DeviceResponse findById(Long id) {
        return toResponse(getEntity(id));
    }

    public DeviceResponse create(DeviceRequest request, MultipartFile photo) {
        validatePhoto(photo, true);
        var atelier = atelierService.requireCurrentAtelier();
        String nom = request.getNom().trim();
        String reference = request.getReference().trim();
        ensureUniqueNom(nom, atelier.getId(), null);
        ensureUniqueReference(reference, atelier.getId(), null);
        Sfm sfm = sfmService.getEntity(request.getSfmId());
        Mas mas = masService.getEntity(request.getMasId());
        ensureSfmCoversMas(sfm, mas);
        var marque = inheritMarqueFromMas(mas);
        MultipartFile optimizedPhoto = imageOptimizationService.optimize(photo);
        StorageService.StoredObject stored = storageService.store(optimizedPhoto);

        Device entity = Device.builder()
                .nom(nom)
                .reference(reference)
                .usage(request.getUsage().trim())
                .dateAcquisition(request.getDateAcquisition())
                .obsolete(Boolean.TRUE.equals(request.getObsolete()))
                .photoKey(stored.key())
                .photoUrl(stored.url())
                .contentType(stored.contentType())
                .fileSize(stored.size())
                .sfm(sfm)
                .mas(mas)
                .marque(marque)
                .atelier(atelier)
                .build();
        Device saved = deviceRepository.save(entity);
        log.info("Pièce créée: {} / {} (atelier={})", saved.getNom(), saved.getReference(), atelier.getId());
        return toResponse(saved);
    }

    public DeviceResponse update(Long id, DeviceRequest request, MultipartFile photo) {
        Device entity = getEntity(id);
        String nom = request.getNom().trim();
        String reference = request.getReference().trim();
        ensureUniqueNom(nom, entity.getAtelier().getId(), entity.getId());
        ensureUniqueReference(reference, entity.getAtelier().getId(), entity.getId());
        entity.setNom(nom);
        entity.setReference(reference);
        entity.setUsage(request.getUsage().trim());
        entity.setDateAcquisition(request.getDateAcquisition());
        entity.setObsolete(Boolean.TRUE.equals(request.getObsolete()));
        Sfm sfm = sfmService.getEntity(request.getSfmId());
        Mas mas = masService.getEntity(request.getMasId());
        ensureSfmCoversMas(sfm, mas);
        entity.setSfm(sfm);
        entity.setMas(mas);
        entity.setMarque(inheritMarqueFromMas(mas));

        if (photo != null && !photo.isEmpty()) {
            validatePhoto(photo, false);
            if (entity.getPhotoKey() != null) {
                storageService.delete(entity.getPhotoKey());
            }
            MultipartFile optimizedPhoto = imageOptimizationService.optimize(photo);
            StorageService.StoredObject stored = storageService.store(optimizedPhoto);
            entity.setPhotoKey(stored.key());
            entity.setPhotoUrl(stored.url());
            entity.setContentType(stored.contentType());
            entity.setFileSize(stored.size());
        }

        return toResponse(deviceRepository.save(entity));
    }

    public void delete(Long id) {
        Device entity = getEntity(id);
        if (entity.getPhotoKey() != null) {
            storageService.delete(entity.getPhotoKey());
        }
        deviceRepository.delete(entity);
    }

    private void validatePhoto(MultipartFile photo, boolean required) {
        if (photo == null || photo.isEmpty()) {
            if (required) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La photo est obligatoire");
            }
            return;
        }
        String contentType = photo.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Le fichier doit être une image");
        }
    }

    private Device getEntity(Long id) {
        Long atelierId = atelierService.requireCurrentAtelier().getId();
        return deviceRepository.findByIdWithRelations(id, atelierId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Device introuvable"));
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
        boolean exists = excludeId == null
                ? deviceRepository.existsByReferenceIgnoreCaseAndAtelierId(reference, atelierId)
                : deviceRepository.existsByReferenceIgnoreCaseAndAtelierIdAndIdNot(reference, atelierId, excludeId);
        if (exists) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Référence déjà utilisée dans cet atelier");
        }
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

    private DeviceResponse toResponse(Device entity) {
        MarqueMas marque = entity.getMarque() != null
                ? entity.getMarque()
                : (entity.getMas().getMarque());
        String marqueLabel = marque != null ? marque.getLabel() : null;
        return DeviceResponse.builder()
                .id(entity.getId())
                .nom(entity.getNom())
                .reference(entity.getReference())
                .usage(entity.getUsage())
                .dateAcquisition(entity.getDateAcquisition())
                .obsolete(entity.isObsolete())
                .photoUrl(entity.getPhotoUrl())
                .contentType(entity.getContentType())
                .fileSize(entity.getFileSize())
                .sfmId(entity.getSfm().getId())
                .sfmNom(entity.getSfm().getNom())
                .masId(entity.getMas().getId())
                .masNumero(entity.getMas().getNumero())
                .masMarque(entity.getMas().getMarque() != null ? entity.getMas().getMarque().getLabel() : marqueLabel)
                .marqueId(marque != null ? marque.getId() : null)
                .marque(marque != null ? marque.getCode() : null)
                .marqueLabel(marqueLabel)
                .build();
    }
}
