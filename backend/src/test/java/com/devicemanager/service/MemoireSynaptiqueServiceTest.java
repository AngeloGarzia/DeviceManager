package com.devicemanager.service;

import com.devicemanager.dto.MemoireSynaptiqueResponse;
import com.devicemanager.entity.Atelier;
import com.devicemanager.entity.AtelierMemoireSynaptique;
import com.devicemanager.event.AtelierSituationEvent;
import com.devicemanager.repository.ArretMaintenanceRepository;
import com.devicemanager.repository.AtelierMemoireSynaptiqueRepository;
import com.devicemanager.repository.CommandeRepository;
import com.devicemanager.repository.DeviceRepository;
import com.devicemanager.repository.InterventionRepository;
import com.devicemanager.repository.InterventionTechniqueRepository;
import com.devicemanager.repository.MasRepository;
import com.devicemanager.repository.SfmRepository;
import com.devicemanager.repository.TodoTacheRepository;
import com.devicemanager.support.TestFixtures;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MemoireSynaptiqueServiceTest {

    @Mock private AtelierMemoireSynaptiqueRepository memoireRepository;
    @Mock private AtelierService atelierService;
    @Mock private DeviceRepository deviceRepository;
    @Mock private MasRepository masRepository;
    @Mock private SfmRepository sfmRepository;
    @Mock private TodoTacheRepository todoTacheRepository;
    @Mock private CommandeRepository commandeRepository;
    @Mock private InterventionRepository interventionRepository;
    @Mock private InterventionTechniqueRepository interventionTechniqueRepository;
    @Mock private ArretMaintenanceRepository arretMaintenanceRepository;
    @Spy private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private MemoireSynaptiqueService memoireSynaptiqueService;

    private Atelier atelier;

    @BeforeEach
    void setUp() {
        atelier = TestFixtures.atelier();
        when(atelierService.requireCurrentAtelier()).thenReturn(atelier);
        stubCounts();
    }

    @Test
    void onSituationEvent_persistsFactAndOverview() {
        when(memoireRepository.findByAtelierId(atelier.getId())).thenReturn(Optional.empty());
        when(memoireRepository.save(any(AtelierMemoireSynaptique.class))).thenAnswer(inv -> {
            AtelierMemoireSynaptique row = inv.getArgument(0);
            if (row.getId() == null) {
                row.setId(1L);
            }
            return row;
        });

        memoireSynaptiqueService.onSituationEvent(
                new AtelierSituationEvent(atelier.getId(), "DEVICE_CREATED", "Nouvelle pièce X"));

        ArgumentCaptor<AtelierMemoireSynaptique> captor =
                ArgumentCaptor.forClass(AtelierMemoireSynaptique.class);
        verify(memoireRepository, org.mockito.Mockito.atLeastOnce()).save(captor.capture());
        AtelierMemoireSynaptique last = captor.getValue();
        assertThat(last.getLastEventType()).isEqualTo("DEVICE_CREATED");
        assertThat(last.getFactsJson()).contains("Nouvelle pièce X");
        assertThat(last.getOverview()).contains("Situation atelier");
    }

    @Test
    void contextForAiChat_includesOverviewAndRelevantFacts() {
        AtelierMemoireSynaptique row = AtelierMemoireSynaptique.builder()
                .id(1L)
                .atelier(atelier)
                .overview("Situation atelier « Test ».")
                .factsJson("[\"2026-03-20 10:00 [STOCK_ADJUSTED] Stock pièce moteur : 2 → 0\","
                        + "\"2026-03-19 09:00 [TODO_CREATED] Tâche ouverte\"]")
                .build();
        when(memoireRepository.findByAtelierId(atelier.getId())).thenReturn(Optional.of(row));
        when(memoireRepository.save(any(AtelierMemoireSynaptique.class))).thenAnswer(inv -> inv.getArgument(0));

        String ctx = memoireSynaptiqueService.contextForAiChat("Quelle pièce est en rupture de stock moteur ?");

        assertThat(ctx).contains("Situation atelier");
        assertThat(ctx).containsIgnoringCase("moteur");
    }

    @Test
    void currentForApi_returnsEmptyMessageWhenMissing() {
        when(memoireRepository.findByAtelierId(atelier.getId())).thenReturn(Optional.empty());

        MemoireSynaptiqueResponse res = memoireSynaptiqueService.currentForApi();

        assertThat(res.getAtelierId()).isEqualTo(atelier.getId());
        assertThat(res.getOverview()).contains("non initialisée");
        assertThat(res.getRecentFacts()).isEmpty();
    }

    private void stubCounts() {
        lenient().when(deviceRepository.countByAtelierId(anyLong())).thenReturn(10L);
        lenient().when(deviceRepository.countByAtelierIdAndObsoleteTrue(anyLong())).thenReturn(1L);
        lenient().when(deviceRepository.countZeroStockByAtelierId(anyLong())).thenReturn(2L);
        lenient().when(masRepository.countByAtelierId(anyLong())).thenReturn(5L);
        lenient().when(sfmRepository.countByAtelierId(anyLong())).thenReturn(3L);
        lenient().when(todoTacheRepository.countByAtelierIdAndStatutIn(anyLong(), anyCollection())).thenReturn(4L);
        lenient().when(commandeRepository.countByAtelierIdAndStatusIn(anyLong(), anyList())).thenReturn(1L);
        lenient().when(interventionRepository.countByAtelierId(anyLong())).thenReturn(7L);
        lenient().when(interventionTechniqueRepository.countByAtelierId(anyLong())).thenReturn(6L);
        lenient().when(arretMaintenanceRepository.countByAtelierIdAndDateHeureRepriseIsNull(anyLong())).thenReturn(0L);
    }
}
