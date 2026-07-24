package com.devicemanager.service;

import com.devicemanager.dto.MarqueMasRequest;
import com.devicemanager.dto.MarqueMasResponse;
import com.devicemanager.dto.MasRequest;
import com.devicemanager.dto.MasResponse;
import com.devicemanager.entity.Atelier;
import com.devicemanager.entity.MarqueMas;
import com.devicemanager.entity.Mas;
import com.devicemanager.repository.MarqueMasRepository;
import com.devicemanager.repository.MasRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class MasService {

    private final MasRepository masRepository;
    private final MarqueMasRepository marqueMasRepository;
    private final AtelierService atelierService;

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
        log.info("Marque MAS créée: {} ({})", saved.getLabel(), saved.getCode());
        return toMarqueResponse(saved);
    }

    public MasResponse create(MasRequest request) {
        Atelier atelier = atelierService.requireCurrentAtelier();
        String numero = request.getNumero().trim();
        ensureUniqueNumero(numero, atelier.getId(), null);
        Mas entity = Mas.builder()
                .numero(numero)
                .marque(getMarque(request.getMarqueId()))
                .utilise(Boolean.TRUE.equals(request.getUtilise()))
                .atelier(atelier)
                .build();
        Mas saved = masRepository.save(entity);
        log.info("MAS créé: {} (atelier={})", saved.getNumero(), atelier.getId());
        return toResponse(saved);
    }

    public MasResponse update(Long id, MasRequest request) {
        Mas entity = getEntity(id);
        String numero = request.getNumero().trim();
        ensureUniqueNumero(numero, entity.getAtelier().getId(), entity.getId());
        entity.setNumero(numero);
        entity.setMarque(getMarque(request.getMarqueId()));
        entity.setUtilise(Boolean.TRUE.equals(request.getUtilise()));
        return toResponse(masRepository.save(entity));
    }

    public void delete(Long id) {
        Mas entity = getEntity(id);
        masRepository.delete(entity);
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
        return MasResponse.builder()
                .id(entity.getId())
                .numero(entity.getNumero())
                .marqueId(marque != null ? marque.getId() : null)
                .marque(marque != null ? marque.getCode() : null)
                .marqueLabel(marque != null ? marque.getLabel() : null)
                .utilise(entity.isUtilise())
                .build();
    }

    private MarqueMasResponse toMarqueResponse(MarqueMas marque) {
        return MarqueMasResponse.builder()
                .id(marque.getId())
                .code(marque.getCode())
                .label(marque.getLabel())
                .value(marque.getId())
                .build();
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
}
