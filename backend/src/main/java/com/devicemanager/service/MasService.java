package com.devicemanager.service;

import com.devicemanager.dto.DenoRequest;
import com.devicemanager.dto.DenoResponse;
import com.devicemanager.dto.MarqueMasRequest;
import com.devicemanager.dto.MarqueMasResponse;
import com.devicemanager.dto.MasRequest;
import com.devicemanager.dto.MasResponse;
import com.devicemanager.entity.Atelier;
import com.devicemanager.entity.Deno;
import com.devicemanager.entity.MarqueMas;
import com.devicemanager.entity.Mas;
import com.devicemanager.entity.MasStatut;
import com.devicemanager.repository.DenoRepository;
import com.devicemanager.repository.MarqueMasRepository;
import com.devicemanager.repository.MasRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.Normalizer;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

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

    private final MasRepository masRepository;
    private final MarqueMasRepository marqueMasRepository;
    private final DenoRepository denoRepository;
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

    public MasResponse create(MasRequest request) {
        Atelier atelier = atelierService.requireCurrentAtelier();
        String numero = request.getNumero().trim();
        ensureUniqueNumero(numero, atelier.getId(), null);
        Mas entity = Mas.builder()
                .numero(numero)
                .numeroSocle(trimToNull(request.getNumeroSocle()))
                .tauxRedistribution(normalizeTaux(request.getTauxRedistribution()))
                .marque(getMarque(request.getMarqueId()))
                .deno(resolveOptionalDeno(request.getDenoId()))
                .atelier(atelier)
                .build();
        entity.applyStatut(resolveStatut(request));
        Mas saved = masRepository.save(entity);
        log.info("Création en base — MAS id={} numero={} marque={} atelier={}",
                saved.getId(),
                saved.getNumero(),
                saved.getMarque() != null ? saved.getMarque().getLabel() : null,
                atelier.getId());
        return toResponse(saved);
    }

    public MasResponse update(Long id, MasRequest request) {
        Mas entity = getEntity(id);
        String numero = request.getNumero().trim();
        ensureUniqueNumero(numero, entity.getAtelier().getId(), entity.getId());
        entity.setNumero(numero);
        entity.setNumeroSocle(trimToNull(request.getNumeroSocle()));
        entity.setTauxRedistribution(normalizeTaux(request.getTauxRedistribution()));
        entity.setMarque(getMarque(request.getMarqueId()));
        entity.setDeno(resolveOptionalDeno(request.getDenoId()));
        entity.applyStatut(resolveStatut(request));
        Mas saved = masRepository.save(entity);
        log.info("Modification en base — MAS id={} numero={} marque={} atelier={}",
                saved.getId(),
                saved.getNumero(),
                saved.getMarque() != null ? saved.getMarque().getLabel() : null,
                saved.getAtelier() != null ? saved.getAtelier().getId() : null);
        return toResponse(saved);
    }

    public void delete(Long id) {
        Mas entity = getEntity(id);
        Long atelierId = entity.getAtelier() != null ? entity.getAtelier().getId() : null;
        String numero = entity.getNumero();
        masRepository.delete(entity);
        log.info("Suppression en base — MAS id={} numero={} atelier={}", id, numero, atelierId);
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
        return MasResponse.builder()
                .id(entity.getId())
                .numero(entity.getNumero())
                .numeroSocle(entity.getNumeroSocle())
                .tauxRedistribution(entity.getTauxRedistribution())
                .marqueId(marque != null ? marque.getId() : null)
                .marque(marque != null ? marque.getCode() : null)
                .marqueLabel(marque != null ? marque.getLabel() : null)
                .denoId(deno != null ? deno.getId() : null)
                .denoValeur(deno != null ? deno.getValeur() : null)
                .denoLabel(deno != null ? deno.getLabel() : null)
                .statut(entity.getStatut() != null ? entity.getStatut().name() : MasStatut.UTILISEE.name())
                .statutLabel(entity.getStatut() != null ? entity.getStatut().label() : MasStatut.UTILISEE.label())
                .utilise(entity.isUtilise())
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
