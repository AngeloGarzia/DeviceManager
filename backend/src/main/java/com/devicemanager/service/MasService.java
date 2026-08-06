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
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Service métier des MAS (Machines À Sous) et marques associées.
 * <p>
 * Les MAS sont rattachées à l'atelier courant ({@code X-Atelier-Id}) ;
 * les marques constituent un référentiel global partagé entre ateliers.
 */
@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class MasService {

    private final MasRepository masRepository;
    private final MarqueMasRepository marqueMasRepository;
    private final AtelierService atelierService;

    /**
     * Liste les MAS de l'atelier courant, avec recherche optionnelle.
     *
     * @param q filtre textuel
     * @return MAS de l'atelier actif
     */
    @Transactional(readOnly = true)
    public List<MasResponse> findAll(String q) {
        Long atelierId = atelierService.requireCurrentAtelier().getId();
        List<Mas> list = (q == null || q.isBlank())
                ? masRepository.findAllByAtelierId(atelierId)
                : masRepository.search(atelierId, q.trim());
        return list.stream().map(this::toResponse).toList();
    }

    /**
     * Retourne une MAS par identifiant dans l'atelier courant.
     *
     * @param id identifiant de la MAS
     * @return fiche MAS
     * @throws org.springframework.web.server.ResponseStatusException {@code 404} si introuvable
     */
    @Transactional(readOnly = true)
    public MasResponse findById(Long id) {
        return toResponse(getEntity(id));
    }

    /**
     * Liste toutes les marques MAS (référentiel global).
     *
     * @return marques triées par libellé
     */
    @Transactional(readOnly = true)
    public List<MarqueMasResponse> listMarques() {
        return marqueMasRepository.findAllByOrderByLabelAsc().stream()
                .sorted(Comparator.comparing(MarqueMas::getLabel, String.CASE_INSENSITIVE_ORDER))
                .map(this::toMarqueResponse)
                .toList();
    }

    /**
     * Crée une marque MAS avec code dérivé du libellé.
     *
     * @param request libellé de la marque
     * @return marque créée
     * @throws org.springframework.web.server.ResponseStatusException {@code 409} si libellé en doublon
     */
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

    /**
     * Crée une MAS dans l'atelier courant.
     *
     * @param request numéro, marque et statut
     * @return MAS créée
     * @throws org.springframework.web.server.ResponseStatusException {@code 409} si numéro en doublon
     */
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
        log.info("Création en base — MAS id={} numero={} marque={} atelier={}",
                saved.getId(),
                saved.getNumero(),
                saved.getMarque() != null ? saved.getMarque().getLabel() : null,
                atelier.getId());
        return toResponse(saved);
    }

    /**
     * Met à jour une MAS de l'atelier courant.
     *
     * @param id identifiant de la MAS
     * @param request données mises à jour
     * @return MAS modifiée
     * @throws org.springframework.web.server.ResponseStatusException {@code 404} si introuvable ;
     *         {@code 409} en cas de conflit de numéro
     */
    public MasResponse update(Long id, MasRequest request) {
        Mas entity = getEntity(id);
        String numero = request.getNumero().trim();
        ensureUniqueNumero(numero, entity.getAtelier().getId(), entity.getId());
        entity.setNumero(numero);
        entity.setMarque(getMarque(request.getMarqueId()));
        entity.setUtilise(Boolean.TRUE.equals(request.getUtilise()));
        Mas saved = masRepository.save(entity);
        log.info("Modification en base — MAS id={} numero={} marque={} atelier={}",
                saved.getId(),
                saved.getNumero(),
                saved.getMarque() != null ? saved.getMarque().getLabel() : null,
                saved.getAtelier() != null ? saved.getAtelier().getId() : null);
        return toResponse(saved);
    }

    /**
     * Supprime une MAS de l'atelier courant.
     *
     * @param id identifiant de la MAS
     * @throws org.springframework.web.server.ResponseStatusException {@code 404} si introuvable
     */
    public void delete(Long id) {
        Mas entity = getEntity(id);
        Long atelierId = entity.getAtelier() != null ? entity.getAtelier().getId() : null;
        String numero = entity.getNumero();
        masRepository.delete(entity);
        log.info("Suppression en base — MAS id={} numero={} atelier={}", id, numero, atelierId);
    }

    /**
     * Charge l'entité MAS pour usage interne (ex. liaison pièce détachée).
     *
     * @param id identifiant de la MAS
     * @return entité persistée
     * @throws org.springframework.web.server.ResponseStatusException {@code 404} si introuvable dans l'atelier
     */
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
