package com.devicemanager.service;

import com.devicemanager.dto.TimelineEventResponse;
import com.devicemanager.entity.Commande;
import com.devicemanager.entity.CommandeLigne;
import com.devicemanager.entity.Device;
import com.devicemanager.entity.Intervention;
import com.devicemanager.entity.InterventionLigne;
import com.devicemanager.entity.StockMouvement;
import com.devicemanager.repository.CommandeRepository;
import com.devicemanager.repository.FitRepository;
import com.devicemanager.repository.InterventionRepository;
import com.devicemanager.repository.InterventionTechniqueRepository;
import com.devicemanager.repository.MasRepository;
import com.devicemanager.repository.StockMouvementRepository;
import com.devicemanager.security.Roles;
import com.devicemanager.security.StockMouvementSources;
import com.devicemanager.security.TimelineEventTypes;
import com.devicemanager.support.TestFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TimelineServiceTest {

    @Mock private CommandeRepository commandeRepository;
    @Mock private InterventionRepository interventionRepository;
    @Mock private InterventionTechniqueRepository interventionTechniqueRepository;
    @Mock private FitRepository fitRepository;
    @Mock private MasRepository masRepository;
    @Mock private StockMouvementRepository stockMouvementRepository;
    @Mock private AtelierService atelierService;
    @InjectMocks private TimelineService timelineService;

    @BeforeEach
    void setUp() {
        when(atelierService.requireCurrentAtelier()).thenReturn(TestFixtures.atelier());
    }

    @Test
    void findEvents_mergesAndSortsDescending() {
        Device device = TestFixtures.device();
        LocalDateTime t1 = LocalDateTime.of(2026, 8, 1, 10, 0);
        LocalDateTime t2 = LocalDateTime.of(2026, 8, 2, 11, 0);
        LocalDateTime t3 = LocalDateTime.of(2026, 8, 3, 12, 0);
        LocalDateTime t4 = LocalDateTime.of(2026, 8, 4, 13, 0);

        Commande commande = Commande.builder()
                .id(1L)
                .technicien(TestFixtures.user("tech", Roles.TECHNICIEN))
                .technicienNom("User Demo")
                .message("msg")
                .dateDemande(t1)
                .dateValidation(t2)
                .dateReception(t3)
                .status("RECEIVED")
                .atelier(TestFixtures.atelier())
                .lignes(new ArrayList<>())
                .build();
        commande.addLigne(CommandeLigne.builder().device(device).quantite(2).build());

        Intervention intervention = Intervention.builder()
                .id(9L)
                .numero("BI-100-2026-00001")
                .dateIntervention(t4)
                .technicien(TestFixtures.user("tech", Roles.TECHNICIEN))
                .technicienNom("User Demo")
                .atelier(TestFixtures.atelier())
                .motif("Panne")
                .travaux("Réparé")
                .lignes(new ArrayList<>())
                .build();
        intervention.addLigne(InterventionLigne.builder()
                .device(device)
                .pieceNom(device.getNom())
                .pieceReference(device.getReference())
                .quantite(1)
                .stockAvant(5)
                .stockApres(4)
                .build());

        StockMouvement manual = StockMouvement.builder()
                .id(5L)
                .atelier(TestFixtures.atelier())
                .device(device)
                .pieceNom(device.getNom())
                .pieceReference(device.getReference())
                .delta(3)
                .stockAvant(4)
                .stockApres(7)
                .sourceType(StockMouvementSources.MANUAL)
                .sourceId(40L)
                .acteurNom("Admin")
                .createdAt(t3.plusHours(1))
                .build();

        when(commandeRepository.findAllWithRelationsOrderByDateDesc(100L)).thenReturn(List.of(commande));
        when(interventionRepository.findAllWithRelationsByAtelierId(100L)).thenReturn(List.of(intervention));
        when(interventionTechniqueRepository.findAllByAtelierId(100L)).thenReturn(List.of());
        when(fitRepository.findAllByAtelierId(100L)).thenReturn(List.of());
        when(stockMouvementRepository.findByAtelierAndSourceType(
                eq(100L), eq(StockMouvementSources.MANUAL)))
                .thenReturn(List.of(manual));

        List<TimelineEventResponse> events = timelineService.findEvents(null, null, null, null);

        assertThat(events).extracting(TimelineEventResponse::getType)
                .containsExactly(
                        TimelineEventTypes.INTERVENTION,
                        TimelineEventTypes.STOCK_ADJUSTMENT,
                        TimelineEventTypes.ORDER_RECEIVED,
                        TimelineEventTypes.ORDER_VALIDATED,
                        TimelineEventTypes.ORDER_REQUEST);
        assertThat(events.getFirst().getColumn()).isEqualTo(TimelineEventTypes.COL_BONS);
    }

    @Test
    void findEvents_filtersTypesAndSkipsOldOrdersWithoutDates() {
        Commande old = Commande.builder()
                .id(2L)
                .technicien(TestFixtures.user("tech", Roles.TECHNICIEN))
                .technicienNom("tech")
                .message("old")
                .dateDemande(LocalDateTime.of(2026, 7, 1, 9, 0))
                .status("RECEIVED")
                .atelier(TestFixtures.atelier())
                .lignes(new ArrayList<>())
                .build();

        when(commandeRepository.findAllWithRelationsOrderByDateDesc(100L)).thenReturn(List.of(old));

        List<TimelineEventResponse> events = timelineService.findEvents(
                null, null, List.of(TimelineEventTypes.ORDER_RECEIVED, TimelineEventTypes.ORDER_REQUEST), null);

        assertThat(events).hasSize(1);
        assertThat(events.getFirst().getType()).isEqualTo(TimelineEventTypes.ORDER_REQUEST);
        assertThat(events.getFirst().getColumn()).isEqualTo(TimelineEventTypes.COL_COMMANDES);
    }
}
