package com.devicemanager.service;

import com.devicemanager.dto.AdminPurgeExecuteRequest;
import com.devicemanager.dto.AdminPurgePlanResponse;
import com.devicemanager.dto.AdminPurgeResultResponse;
import com.devicemanager.entity.Atelier;
import com.devicemanager.entity.Device;
import com.devicemanager.entity.Intervention;
import com.devicemanager.entity.InterventionLigne;
import com.devicemanager.entity.StockMouvement;
import com.devicemanager.repository.CommandeLigneRepository;
import com.devicemanager.repository.DevicePrixAlerteRepository;
import com.devicemanager.repository.DevicePrixObservationRepository;
import com.devicemanager.repository.DeviceRepository;
import com.devicemanager.repository.InterventionLigneRepository;
import com.devicemanager.repository.InterventionRepository;
import com.devicemanager.repository.StockMouvementRepository;
import com.devicemanager.support.TestFixtures;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminPurgeServiceTest {

    @Mock private DeviceRepository deviceRepository;
    @Mock private InterventionLigneRepository interventionLigneRepository;
    @Mock private InterventionRepository interventionRepository;
    @Mock private StockMouvementRepository stockMouvementRepository;
    @Mock private CommandeLigneRepository commandeLigneRepository;
    @Mock private DevicePrixAlerteRepository devicePrixAlerteRepository;
    @Mock private DevicePrixObservationRepository devicePrixObservationRepository;
    @Mock private AtelierService atelierService;
    @Mock private StorageService storageService;
    @Mock private Clock clock;
    @InjectMocks private AdminPurgeService adminPurgeService;

    @Test
    void previewDevice_archivesBonsAndDetachesHistory() {
        Atelier atelier = TestFixtures.atelier();
        Device device = Device.builder()
                .id(50L)
                .nom("Verrou")
                .reference("VR-1")
                .atelier(atelier)
                .photos(new ArrayList<>())
                .build();
        Intervention bi = Intervention.builder()
                .id(9L)
                .numero("BI-1")
                .archived(false)
                .atelier(atelier)
                .build();
        InterventionLigne ligne = InterventionLigne.builder()
                .id(1L)
                .intervention(bi)
                .device(device)
                .pieceNom("Verrou")
                .quantite(1)
                .stockAvant(2)
                .stockApres(1)
                .build();

        when(atelierService.requireCurrentAtelier()).thenReturn(atelier);
        when(deviceRepository.findByIdWithRelations(50L, atelier.getId())).thenReturn(Optional.of(device));
        when(interventionLigneRepository.findByDeviceIdWithIntervention(50L)).thenReturn(List.of(ligne));
        when(stockMouvementRepository.countByDeviceId(50L)).thenReturn(3L);
        when(commandeLigneRepository.findByDeviceIdWithCommande(50L)).thenReturn(List.of());
        when(devicePrixAlerteRepository.countByDeviceId(50L)).thenReturn(0L);
        when(devicePrixObservationRepository.countByDeviceId(50L)).thenReturn(0L);

        AdminPurgePlanResponse plan = adminPurgeService.preview("DEVICE", 50L);

        assertThat(plan.isExecutable()).isTrue();
        assertThat(plan.getConfirmPhrase()).isEqualTo("SUPPRIMER DEVICE #50");
        assertThat(plan.getNodes()).anyMatch(n -> "ARCHIVE".equals(n.getAction()) && "INTERVENTION".equals(n.getEntityType()));
        assertThat(plan.getNodes()).anyMatch(n -> "DETACH".equals(n.getAction()) && "STOCK_MOUVEMENT".equals(n.getEntityType()));
        assertThat(plan.getNodes()).anyMatch(n -> "DELETE".equals(n.getAction()) && "DEVICE".equals(n.getEntityType()));
    }

    @Test
    void executeDevice_archivesBonKeepsStockDetached() {
        when(clock.instant()).thenReturn(Instant.parse("2026-10-15T08:00:00Z"));
        when(clock.getZone()).thenReturn(ZoneOffset.UTC);

        Atelier atelier = TestFixtures.atelier();
        Device device = Device.builder()
                .id(50L)
                .nom("Verrou")
                .reference("VR-1")
                .atelier(atelier)
                .photos(new ArrayList<>())
                .documents(new ArrayList<>())
                .build();
        Intervention bi = Intervention.builder()
                .id(9L)
                .numero("BI-1")
                .archived(false)
                .atelier(atelier)
                .build();
        InterventionLigne ligne = InterventionLigne.builder()
                .id(1L)
                .intervention(bi)
                .device(device)
                .pieceNom("Verrou")
                .quantite(1)
                .stockAvant(2)
                .stockApres(1)
                .build();
        StockMouvement mvt = StockMouvement.builder()
                .id(3L)
                .atelier(atelier)
                .device(device)
                .pieceNom("Verrou")
                .delta(-1)
                .stockAvant(2)
                .stockApres(1)
                .sourceType("INTERVENTION")
                .createdAt(LocalDateTime.now())
                .build();

        when(atelierService.requireCurrentAtelier()).thenReturn(atelier);
        when(deviceRepository.findByIdWithRelations(50L, atelier.getId())).thenReturn(Optional.of(device));
        when(interventionLigneRepository.findByDeviceIdWithIntervention(50L)).thenReturn(List.of(ligne));
        when(stockMouvementRepository.countByDeviceId(50L)).thenReturn(1L);
        when(commandeLigneRepository.findByDeviceIdWithCommande(50L)).thenReturn(List.of());
        when(devicePrixAlerteRepository.countByDeviceId(50L)).thenReturn(0L);
        when(devicePrixObservationRepository.countByDeviceId(50L)).thenReturn(0L);
        when(interventionLigneRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(interventionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(stockMouvementRepository.findByDeviceId(50L)).thenReturn(List.of(mvt));
        when(stockMouvementRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        AdminPurgePlanResponse plan = adminPurgeService.preview("DEVICE", 50L);
        AdminPurgeExecuteRequest req = new AdminPurgeExecuteRequest();
        req.setRootType("DEVICE");
        req.setRootId(50L);
        req.setConfirmPhrase(plan.getConfirmPhrase());

        AdminPurgeResultResponse result = adminPurgeService.execute(req, "super");

        assertThat(bi.isArchived()).isTrue();
        assertThat(ligne.getDevice()).isNull();
        assertThat(mvt.getDevice()).isNull();
        verify(deviceRepository).delete(device);
        verify(stockMouvementRepository, never()).delete(any());
        assertThat(result.getMessage()).contains("archivés");
    }
}
