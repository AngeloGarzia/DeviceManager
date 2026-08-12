package com.devicemanager.service;

import com.devicemanager.dto.InterventionRequest;
import com.devicemanager.dto.InterventionResponse;
import com.devicemanager.entity.Device;
import com.devicemanager.entity.Intervention;
import com.devicemanager.entity.User;
import com.devicemanager.repository.DeviceRepository;
import com.devicemanager.repository.InterventionRepository;
import com.devicemanager.repository.UserRepository;
import com.devicemanager.security.Roles;
import com.devicemanager.support.TestFixtures;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InterventionServiceTest {

    @Mock private InterventionRepository interventionRepository;
    @Mock private DeviceRepository deviceRepository;
    @Mock private UserRepository userRepository;
    @Mock private AtelierService atelierService;
    @Mock private StockMouvementService stockMouvementService;
    @InjectMocks private InterventionService interventionService;

    @Test
    void create_consumesStockAndArchivesBon() {
        User tech = TestFixtures.user("tech", Roles.TECHNICIEN);
        Device device = TestFixtures.device();
        device.setStock(5);
        when(userRepository.findByUsername("tech")).thenReturn(Optional.of(tech));
        when(atelierService.requireCurrentAtelier()).thenReturn(TestFixtures.atelier());
        when(interventionRepository.countByAtelierIdAndYear(eq(100L), eq(LocalDateTime.now().getYear())))
                .thenReturn(3L);
        when(deviceRepository.findByIdWithRelations(40L, 100L)).thenReturn(Optional.of(device));
        when(deviceRepository.save(any(Device.class))).thenAnswer(inv -> inv.getArgument(0));
        when(interventionRepository.saveAndFlush(any(Intervention.class))).thenAnswer(inv -> {
            Intervention i = inv.getArgument(0);
            i.setId(10L);
            return i;
        });
        when(stockMouvementService.record(any(), any(), anyInt(), anyInt(), anyString(), any(), anyString()))
                .thenAnswer(inv -> null);

        InterventionRequest.InterventionLineDto line = new InterventionRequest.InterventionLineDto();
        line.setDeviceId(40L);
        line.setQuantite(2);
        InterventionRequest request = new InterventionRequest();
        request.setDateIntervention(LocalDateTime.of(2026, 8, 10, 14, 30));
        request.setMotif("Panne lecteur billets");
        request.setTravaux("Remplacement carte lecteur");
        request.setEmplacement("Salle machines");
        request.setMachineMas("MAS-001");
        request.setLignes(List.of(line));

        InterventionResponse response = interventionService.create(request, "tech");

        assertThat(device.getStock()).isEqualTo(3);
        assertThat(response.getNumero()).isEqualTo("BI-100-2026-00004");
        assertThat(response.getMotif()).isEqualTo("Panne lecteur billets");
        assertThat(response.getTotalQuantite()).isEqualTo(2);
        assertThat(response.getLignes()).hasSize(1);
        assertThat(response.getLignes().getFirst().getStockAvant()).isEqualTo(5);
        assertThat(response.getLignes().getFirst().getStockApres()).isEqualTo(3);

        ArgumentCaptor<Intervention> captor = ArgumentCaptor.forClass(Intervention.class);
        verify(interventionRepository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getLignes()).hasSize(1);
        verify(stockMouvementService).record(
                any(),
                eq(device),
                eq(5),
                eq(3),
                eq("INTERVENTION"),
                eq(10L),
                anyString());
    }

    @Test
    void create_rejectsInsufficientStock() {
        User tech = TestFixtures.user("tech", Roles.TECHNICIEN);
        Device device = TestFixtures.device();
        device.setStock(1);
        when(userRepository.findByUsername("tech")).thenReturn(Optional.of(tech));
        when(atelierService.requireCurrentAtelier()).thenReturn(TestFixtures.atelier());
        when(interventionRepository.countByAtelierIdAndYear(eq(100L), eq(2026))).thenReturn(0L);
        when(deviceRepository.findByIdWithRelations(40L, 100L)).thenReturn(Optional.of(device));

        InterventionRequest.InterventionLineDto line = new InterventionRequest.InterventionLineDto();
        line.setDeviceId(40L);
        line.setQuantite(3);
        InterventionRequest request = new InterventionRequest();
        request.setDateIntervention(LocalDateTime.of(2026, 3, 1, 10, 0));
        request.setMotif("Test");
        request.setTravaux("Test");
        request.setLignes(List.of(line));

        assertThatThrownBy(() -> interventionService.create(request, "tech"))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getReason())
                .asString()
                .contains("Stock insuffisant");
    }
}
