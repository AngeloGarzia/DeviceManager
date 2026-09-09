package com.devicemanager.service;

import com.devicemanager.dto.MarqueMasRequest;
import com.devicemanager.dto.MasRequest;
import com.devicemanager.dto.MasResponse;
import com.devicemanager.dto.RegleJeuxMasLinkRequest;
import com.devicemanager.dto.RegleJeuxResponse;
import com.devicemanager.entity.MarqueMas;
import com.devicemanager.entity.Mas;
import com.devicemanager.entity.RegleJeux;
import com.devicemanager.repository.DenoRepository;
import com.devicemanager.repository.MarqueMasRepository;
import com.devicemanager.repository.MasRepository;
import com.devicemanager.repository.RegleJeuxRepository;
import com.devicemanager.support.TestFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MasServiceTest {

    private static final Logger log = LoggerFactory.getLogger(MasServiceTest.class);

    @Mock private MasRepository masRepository;
    @Mock private MarqueMasRepository marqueMasRepository;
    @Mock private DenoRepository denoRepository;
    @Mock private RegleJeuxRepository regleJeuxRepository;
    @Mock private AiAssistantService aiAssistantService;
    @Mock private AtelierService atelierService;
    @Mock private StorageService storageService;
    @Mock private FitService fitService;
    @Mock private AtelierMemoirePublisher atelierMemoirePublisher;
    @InjectMocks private MasService masService;

    @BeforeEach
    void setUp() {
        lenient().when(atelierService.requireCurrentAtelier()).thenReturn(TestFixtures.atelier());
        lenient().when(regleJeuxRepository.findAllById(any())).thenAnswer(inv -> {
            Iterable<Long> ids = inv.getArgument(0);
            List<RegleJeux> regles = new java.util.ArrayList<>();
            for (Long id : ids) {
                regles.add(RegleJeux.builder().id(id).code("RJ_" + id).label("Règle " + id)
                        .fileKey("k").fileUrl("/u").originalName("r.pdf").build());
            }
            return regles;
        });
    }

    @Test
    void toCode_normalizesLabel() {
        assertThat(MasService.toCode(" Novomatic Élite ")).isEqualTo("NOVOMATIC_ELITE");
        assertThat(MasService.toCode("@@@")).isEqualTo("MARQUE");
    }

    @Test
    void create_persistsUniqueNumero() {
        log.info("Test create MAS");
        when(masRepository.existsByNumeroIgnoreCaseAndAtelierId("MAS-100", 100L)).thenReturn(false);
        when(marqueMasRepository.findById(5L)).thenReturn(Optional.of(TestFixtures.marque()));
        when(masRepository.save(any(Mas.class))).thenAnswer(inv -> {
            Mas m = inv.getArgument(0);
            m.setId(99L);
            return m;
        });

        MasRequest request = new MasRequest();
        request.setNumero(" MAS-100 ");
        request.setMarqueId(5L);
        request.setUtilise(true);
        request.setRegleJeuxIds(List.of(1L));

        MasResponse response = masService.create(request);

        assertThat(response.getNumero()).isEqualTo("MAS-100");
        assertThat(response.getMarqueLabel()).isEqualTo("Novomatic");
        verify(masRepository).save(any(Mas.class));
        verify(fitService).ensureFitSnapshotForMas(any(Mas.class));
    }

    @Test
    void create_rejectsDuplicateNumero() {
        when(masRepository.existsByNumeroIgnoreCaseAndAtelierId("MAS-001", 100L)).thenReturn(true);

        MasRequest request = new MasRequest();
        request.setNumero("MAS-001");
        request.setMarqueId(5L);
        request.setUtilise(true);
        request.setRegleJeuxIds(List.of(1L));

        assertThatThrownBy(() -> masService.create(request))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    ResponseStatusException rse = (ResponseStatusException) ex;
                    assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(rse.getReason()).isEqualTo("Numéro MAS déjà utilisé dans cet atelier");
                });
    }

    @Test
    void createMarque_generatesUniqueCode() {
        when(marqueMasRepository.existsByLabelIgnoreCase("Aristocrat")).thenReturn(false);
        when(marqueMasRepository.existsByCodeIgnoreCase("ARISTOCRAT")).thenReturn(true);
        when(marqueMasRepository.existsByCodeIgnoreCase("ARISTOCRAT_2")).thenReturn(false);
        when(marqueMasRepository.save(any(MarqueMas.class))).thenAnswer(inv -> {
            MarqueMas m = inv.getArgument(0);
            m.setId(7L);
            return m;
        });

        MarqueMasRequest request = new MarqueMasRequest();
        request.setLabel(" Aristocrat ");
        var response = masService.createMarque(request);

        ArgumentCaptor<MarqueMas> captor = ArgumentCaptor.forClass(MarqueMas.class);
        verify(marqueMasRepository).save(captor.capture());
        assertThat(captor.getValue().getCode()).isEqualTo("ARISTOCRAT_2");
        assertThat(response.getLabel()).isEqualTo("Aristocrat");
    }

    @Test
    void findById_notFound() {
        when(masRepository.findByIdAndAtelierId(1L, 100L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> masService.findById(1L))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getReason())
                .isEqualTo("MAS introuvable");
    }

    @Test
    void findAll_withoutQuery_listsAtelier() {
        when(masRepository.findAllByAtelierId(100L)).thenReturn(List.of(TestFixtures.mas()));

        assertThat(masService.findAll(null)).hasSize(1);
        verify(masRepository).findAllByAtelierId(100L);
    }

    @Test
    void create_multiDeno_setsLabelAndClearsDeno() {
        when(masRepository.existsByNumeroIgnoreCaseAndAtelierId("MAS-MD", 100L)).thenReturn(false);
        when(marqueMasRepository.findById(5L)).thenReturn(Optional.of(TestFixtures.marque()));
        when(masRepository.save(any(Mas.class))).thenAnswer(inv -> {
            Mas m = inv.getArgument(0);
            m.setId(101L);
            return m;
        });

        MasRequest request = new MasRequest();
        request.setNumero("MAS-MD");
        request.setMarqueId(5L);
        request.setMultiDeno(true);
        request.setDenoId(9L);
        request.setUtilise(true);
        request.setRegleJeuxIds(List.of(2L));

        MasResponse response = masService.create(request);

        ArgumentCaptor<Mas> captor = ArgumentCaptor.forClass(Mas.class);
        verify(masRepository).save(captor.capture());
        assertThat(captor.getValue().isMultiDeno()).isTrue();
        assertThat(captor.getValue().getDeno()).isNull();
        assertThat(response.isMultiDeno()).isTrue();
        assertThat(response.getDenoLabel()).isEqualTo(MasService.MULTI_DENO_LABEL);
        assertThat(response.getDenoId()).isNull();
        verify(denoRepository, never()).findById(any());
    }

    @Test
    void create_rejectsMasWithoutRegleJeux() {
        when(masRepository.existsByNumeroIgnoreCaseAndAtelierId("MAS-RJ", 100L)).thenReturn(false);
        when(marqueMasRepository.findById(5L)).thenReturn(Optional.of(TestFixtures.marque()));

        MasRequest request = new MasRequest();
        request.setNumero("MAS-RJ");
        request.setMarqueId(5L);
        request.setUtilise(true);
        request.setRegleJeuxIds(List.of());

        assertThatThrownBy(() -> masService.create(request))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    ResponseStatusException rse = (ResponseStatusException) ex;
                    assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(rse.getReason()).containsIgnoringCase("règle de jeux");
                });
        verify(masRepository, never()).save(any(Mas.class));
    }

    @Test
    void deleteRegleJeux_removesWhenNotLinked() {
        RegleJeux regle = RegleJeux.builder()
                .id(8L)
                .code("RJ8")
                .label("Règle test")
                .fileKey("old-key")
                .fileUrl("/old")
                .originalName("r.pdf")
                .build();
        when(regleJeuxRepository.findById(8L)).thenReturn(java.util.Optional.of(regle));
        when(regleJeuxRepository.countMasLinks(8L)).thenReturn(0L);

        masService.deleteRegleJeux(8L);

        verify(regleJeuxRepository).delete(regle);
        verify(storageService).delete("old-key");
    }

    @Test
    void deleteRegleJeux_rejectsWhenLinkedToMas() {
        RegleJeux regle = RegleJeux.builder()
                .id(9L)
                .code("RJ9")
                .label("Règle liée")
                .fileKey("k")
                .fileUrl("/u")
                .originalName("r.pdf")
                .build();
        when(regleJeuxRepository.findById(9L)).thenReturn(java.util.Optional.of(regle));
        when(regleJeuxRepository.countMasLinks(9L)).thenReturn(2L);

        assertThatThrownBy(() -> masService.deleteRegleJeux(9L))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    ResponseStatusException rse = (ResponseStatusException) ex;
                    assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(rse.getReason()).contains("2");
                });
        verify(regleJeuxRepository, never()).delete(any());
    }

    @Test
    void linkMasToRegleJeux_attachesMasFromCatalogue() {
        RegleJeux regle = RegleJeux.builder()
                .id(10L)
                .code("RJ10")
                .label("Règle catalogue")
                .fileKey("k")
                .fileUrl("/u")
                .originalName("r.pdf")
                .build();
        Mas mas = TestFixtures.mas();
        mas.setReglesJeux(new HashSet<>(List.of(
                RegleJeux.builder().id(1L).code("RJ1").label("Autre").fileKey("k2").fileUrl("/u2").originalName("a.pdf").build()
        )));

        RegleJeuxMasLinkRequest request = new RegleJeuxMasLinkRequest();
        request.setMasIds(List.of(20L));

        when(regleJeuxRepository.findById(10L)).thenReturn(Optional.of(regle));
        when(masRepository.findAllByIdInAndAtelierId(Set.of(20L), 100L)).thenReturn(List.of(mas));
        when(masRepository.findAllByRegleJeuxIdAndAtelierId(10L, 100L))
                .thenReturn(List.of())
                .thenReturn(List.of(mas));
        when(masRepository.findByIdAndAtelierId(20L, 100L)).thenReturn(Optional.of(mas));
        when(masRepository.save(any(Mas.class))).thenAnswer(inv -> inv.getArgument(0));

        RegleJeuxResponse response = masService.linkMasToRegleJeux(10L, request);

        assertThat(response.getMasIds()).containsExactly(20L);
        assertThat(mas.getReglesJeux()).anyMatch(r -> r.getId().equals(10L));
        verify(masRepository).save(mas);
    }

    @Test
    void linkMasToRegleJeux_attachesMasWithoutExistingRules() {
        RegleJeux regle = RegleJeux.builder()
                .id(10L)
                .code("RJ10")
                .label("Règle catalogue")
                .fileKey("k")
                .fileUrl("/u")
                .originalName("r.pdf")
                .build();
        Mas mas = TestFixtures.mas();
        mas.setReglesJeux(new HashSet<>());

        RegleJeuxMasLinkRequest request = new RegleJeuxMasLinkRequest();
        request.setMasIds(List.of(20L));

        when(regleJeuxRepository.findById(10L)).thenReturn(Optional.of(regle));
        when(masRepository.findAllByIdInAndAtelierId(Set.of(20L), 100L)).thenReturn(List.of(mas));
        when(masRepository.findAllByRegleJeuxIdAndAtelierId(10L, 100L))
                .thenReturn(List.of())
                .thenReturn(List.of(mas));
        when(masRepository.findByIdAndAtelierId(20L, 100L)).thenReturn(Optional.of(mas));
        when(masRepository.save(any(Mas.class))).thenAnswer(inv -> inv.getArgument(0));

        RegleJeuxResponse response = masService.linkMasToRegleJeux(10L, request);

        assertThat(response.getMasIds()).containsExactly(20L);
        assertThat(mas.getReglesJeux()).anyMatch(r -> r.getId().equals(10L));
        verify(masRepository).save(mas);
    }

    @Test
    void linkMasToRegleJeux_rejectsDetachingLastRuleFromMas() {
        RegleJeux regle = RegleJeux.builder()
                .id(10L)
                .code("RJ10")
                .label("Seule règle")
                .fileKey("k")
                .fileUrl("/u")
                .originalName("r.pdf")
                .build();
        Mas mas = TestFixtures.mas();
        mas.setReglesJeux(new HashSet<>(List.of(regle)));

        RegleJeuxMasLinkRequest request = new RegleJeuxMasLinkRequest();
        request.setMasIds(List.of());

        when(regleJeuxRepository.findById(10L)).thenReturn(Optional.of(regle));
        when(masRepository.findAllByRegleJeuxIdAndAtelierId(10L, 100L)).thenReturn(List.of(mas));

        assertThatThrownBy(() -> masService.linkMasToRegleJeux(10L, request))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    ResponseStatusException rse = (ResponseStatusException) ex;
                    assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(rse.getReason()).contains("MAS-001");
                });
        verify(masRepository, never()).save(any(Mas.class));
    }

    @Test
    void delete_isForbidden_byBusinessRule() {
        assertThatThrownBy(() -> masService.delete(20L))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    ResponseStatusException rse = (ResponseStatusException) ex;
                    assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);
                    assertThat(rse.getReason()).containsIgnoringCase("statut");
                });
        verify(masRepository, never()).delete(any(Mas.class));
    }
}
