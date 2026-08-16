package com.devicemanager.service;

import com.devicemanager.dto.AiDevisPrixConfirmRequest;
import com.devicemanager.dto.AiDevisPrixConfirmResponse;
import com.devicemanager.dto.AiDevisPrixScanResponse;
import com.devicemanager.dto.AiDevisPrixSuggestion;
import com.devicemanager.dto.DevicePrixAlerteResponse;
import com.devicemanager.dto.DevicePrixObservationResponse;
import com.devicemanager.entity.Commande;
import com.devicemanager.entity.CommandeLigne;
import com.devicemanager.entity.Device;
import com.devicemanager.entity.DevicePrixAlerte;
import com.devicemanager.entity.DevicePrixObservation;
import com.devicemanager.entity.PrixAlerteSeverity;
import com.devicemanager.entity.PrixAlerteStatus;
import com.devicemanager.repository.CommandeRepository;
import com.devicemanager.repository.DevicePrixAlerteRepository;
import com.devicemanager.repository.DevicePrixObservationRepository;
import com.devicemanager.repository.DeviceRepository;
import com.devicemanager.security.OrderStatuses;
import com.devicemanager.support.TestFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DevicePrixServiceTest {

    private static final byte[] PDF_BYTES = "%PDF-1.4 minimal".getBytes();
    private static final byte[] JPEG = new byte[]{
            (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0,
            0, 0, 0, 0, 0, 0, 0, 0
    };

    @Mock private DevicePrixObservationRepository observationRepository;
    @Mock private DevicePrixAlerteRepository alerteRepository;
    @Mock private DeviceRepository deviceRepository;
    @Mock private CommandeRepository commandeRepository;
    @Mock private AtelierService atelierService;
    @Mock private AiAssistantService aiAssistantService;
    @InjectMocks private DevicePrixService devicePrixService;

    @BeforeEach
    void setUp() {
        lenient().when(atelierService.requireCurrentAtelier()).thenReturn(TestFixtures.atelier());
    }

    @Test
    void detectSignals_noHistory_isBaselineOnly() {
        List<String> signals = DevicePrixService.detectSignals(new BigDecimal("10.00"), List.of());
        assertThat(signals).containsExactly("NO_BASELINE");
        assertThat(DevicePrixService.severityFromSignals(signals)).isNull();
    }

    @Test
    void detectSignals_spike_isAlert() {
        DevicePrixObservation last = DevicePrixObservation.builder()
                .unitPriceHt(new BigDecimal("10.00"))
                .build();
        List<String> signals = DevicePrixService.detectSignals(new BigDecimal("15.00"), List.of(last));
        assertThat(signals).contains("SPIKE", "LOW_SAMPLE");
        assertThat(DevicePrixService.severityFromSignals(signals)).isEqualTo(PrixAlerteSeverity.ALERT);
    }

    @Test
    void detectSignals_drop_isAlert() {
        DevicePrixObservation last = DevicePrixObservation.builder()
                .unitPriceHt(new BigDecimal("10.00"))
                .build();
        List<String> signals = DevicePrixService.detectSignals(new BigDecimal("5.00"), List.of(last));
        assertThat(signals).contains("DROP");
        assertThat(DevicePrixService.severityFromSignals(signals)).isEqualTo(PrixAlerteSeverity.ALERT);
    }

    @Test
    void detectSignals_stable_withOnePoint_isWatchOnly() {
        DevicePrixObservation last = DevicePrixObservation.builder()
                .unitPriceHt(new BigDecimal("10.00"))
                .build();
        List<String> signals = DevicePrixService.detectSignals(new BigDecimal("10.50"), List.of(last));
        assertThat(signals).containsExactly("LOW_SAMPLE");
        assertThat(DevicePrixService.severityFromSignals(signals)).isEqualTo(PrixAlerteSeverity.WATCH);
    }

    @Test
    void history_returnsMappedObservations() {
        Device device = TestFixtures.device();
        when(deviceRepository.findByIdWithRelations(40L, 100L)).thenReturn(Optional.of(device));
        when(observationRepository
                .findByDeviceIdAndAtelierIdAndInvalidatedFalseOrderByObservedAtDesc(40L, 100L))
                .thenReturn(List.of(DevicePrixObservation.builder()
                        .id(1L)
                        .device(device)
                        .unitPriceHt(new BigDecimal("12.50"))
                        .currency("EUR")
                        .observedAt(LocalDateTime.of(2026, 1, 1, 10, 0))
                        .confirmedAt(LocalDateTime.of(2026, 1, 1, 11, 0))
                        .confirmedBy("admin")
                        .invalidated(false)
                        .build()));

        List<DevicePrixObservationResponse> hist = devicePrixService.history(40L);

        assertThat(hist).hasSize(1);
        assertThat(hist.getFirst().getUnitPriceHt()).isEqualByComparingTo("12.50");
        assertThat(hist.getFirst().getDeviceNom()).isEqualTo("Carte mère");
    }

    @Test
    void listAlertes_filtersByStatus() {
        Device device = TestFixtures.device();
        DevicePrixObservation obs = DevicePrixObservation.builder()
                .id(9L)
                .device(device)
                .unitPriceHt(new BigDecimal("20.00"))
                .build();
        when(alerteRepository.findByAtelierIdAndStatusOrderByCreatedAtDesc(100L, PrixAlerteStatus.OPEN))
                .thenReturn(List.of(DevicePrixAlerte.builder()
                        .id(3L)
                        .device(device)
                        .observation(obs)
                        .severity(PrixAlerteSeverity.ALERT)
                        .signalsJson("[\"SPIKE\"]")
                        .status(PrixAlerteStatus.OPEN)
                        .createdAt(LocalDateTime.now())
                        .build()));

        List<DevicePrixAlerteResponse> list = devicePrixService.listAlertes("OPEN");

        assertThat(list).hasSize(1);
        assertThat(list.getFirst().getSeverity()).isEqualTo("ALERT");
        assertThat(list.getFirst().getSignals()).contains("SPIKE");
    }

    @Test
    void listAlertes_rejectsInvalidStatus() {
        assertThatThrownBy(() -> devicePrixService.listAlertes("NOPE"))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void acknowledge_updatesStatus() {
        Device device = TestFixtures.device();
        DevicePrixAlerte alerte = DevicePrixAlerte.builder()
                .id(5L)
                .device(device)
                .observation(DevicePrixObservation.builder().id(1L).unitPriceHt(BigDecimal.TEN).build())
                .severity(PrixAlerteSeverity.WATCH)
                .status(PrixAlerteStatus.OPEN)
                .createdAt(LocalDateTime.now())
                .build();
        when(alerteRepository.findByIdAndAtelierId(5L, 100L)).thenReturn(Optional.of(alerte));
        when(alerteRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        DevicePrixAlerteResponse res = devicePrixService.acknowledge(5L, "admin");

        assertThat(res.getStatus()).isEqualTo("ACKNOWLEDGED");
        assertThat(res.getAckBy()).isEqualTo("admin");
    }

    @Test
    void analyzeDevisPrices_rejectsImage() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "scan.jpg", "image/jpeg", JPEG);
        assertThatThrownBy(() -> devicePrixService.analyzeDevisPrices(90L, file))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("PDF");
    }

    @Test
    void analyzeDevisPrices_returnsSuggestionsWithLastPrice() {
        Device device = TestFixtures.device();
        device.setLastUnitPriceHt(new BigDecimal("8.00"));
        Commande commande = validatedCommande(device);
        when(commandeRepository.findByIdWithRelations(90L, 100L)).thenReturn(Optional.of(commande));
        when(aiAssistantService.analyzeDevisPrices(any(), anyList()))
                .thenReturn(AiDevisPrixScanResponse.builder()
                        .enabled(true)
                        .suggestions(List.of(AiDevisPrixSuggestion.builder()
                                .deviceId(40L)
                                .suggestedUnitPriceHt(new BigDecimal("11.00"))
                                .confidence("HIGH")
                                .build()))
                        .unmatched(List.of())
                        .build());
        when(deviceRepository.findByIdWithRelations(40L, 100L)).thenReturn(Optional.of(device));

        MockMultipartFile file = new MockMultipartFile(
                "file", "devis.pdf", "application/pdf", PDF_BYTES);
        AiDevisPrixScanResponse scan = devicePrixService.analyzeDevisPrices(90L, file);

        assertThat(scan.getSuggestions()).hasSize(1);
        assertThat(scan.getSuggestions().getFirst().getLastUnitPriceHt())
                .isEqualByComparingTo("8.00");
    }

    @Test
    void confirmDevisPrices_persistsObservationAndSpikeAlerte() {
        Device device = TestFixtures.device();
        Commande commande = validatedCommande(device);
        when(commandeRepository.findByIdWithRelations(90L, 100L)).thenReturn(Optional.of(commande));
        when(deviceRepository.findByIdWithRelations(40L, 100L)).thenReturn(Optional.of(device));
        when(observationRepository
                .findTop20ByDeviceIdAndAtelierIdAndInvalidatedFalseOrderByObservedAtDesc(40L, 100L))
                .thenReturn(List.of(
                        DevicePrixObservation.builder().unitPriceHt(new BigDecimal("10.00")).build(),
                        DevicePrixObservation.builder().unitPriceHt(new BigDecimal("9.50")).build()
                ));
        when(observationRepository.save(any())).thenAnswer(inv -> {
            DevicePrixObservation o = inv.getArgument(0);
            o.setId(77L);
            return o;
        });
        when(deviceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(alerteRepository.save(any())).thenAnswer(inv -> {
            DevicePrixAlerte a = inv.getArgument(0);
            a.setId(88L);
            return a;
        });
        when(aiAssistantService.isEnabled()).thenReturn(false);
        when(alerteRepository.findByAtelierIdAndStatusOrderByCreatedAtDesc(100L, PrixAlerteStatus.OPEN))
                .thenReturn(List.of());

        AiDevisPrixConfirmRequest.Item item = new AiDevisPrixConfirmRequest.Item();
        item.setDeviceId(40L);
        item.setUnitPriceHt(new BigDecimal("15.00"));
        item.setDevisDesignation("Carte mère IGT");
        AiDevisPrixConfirmRequest request = new AiDevisPrixConfirmRequest();
        request.setItems(List.of(item));

        AiDevisPrixConfirmResponse res = devicePrixService.confirmDevisPrices(90L, request, "admin");

        assertThat(res.getConfirmedCount()).isEqualTo(1);
        assertThat(device.getLastUnitPriceHt()).isEqualByComparingTo("15.00");
        ArgumentCaptor<DevicePrixAlerte> alerteCaptor = ArgumentCaptor.forClass(DevicePrixAlerte.class);
        verify(alerteRepository).save(alerteCaptor.capture());
        assertThat(alerteCaptor.getValue().getSeverity()).isEqualTo(PrixAlerteSeverity.ALERT);
    }

    @Test
    void confirmDevisPrices_baseline_noAlerte() {
        Device device = TestFixtures.device();
        Commande commande = validatedCommande(device);
        when(commandeRepository.findByIdWithRelations(90L, 100L)).thenReturn(Optional.of(commande));
        when(deviceRepository.findByIdWithRelations(40L, 100L)).thenReturn(Optional.of(device));
        when(observationRepository
                .findTop20ByDeviceIdAndAtelierIdAndInvalidatedFalseOrderByObservedAtDesc(40L, 100L))
                .thenReturn(List.of());
        when(observationRepository.save(any())).thenAnswer(inv -> {
            DevicePrixObservation o = inv.getArgument(0);
            o.setId(1L);
            return o;
        });
        when(deviceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(alerteRepository.findByAtelierIdAndStatusOrderByCreatedAtDesc(100L, PrixAlerteStatus.OPEN))
                .thenReturn(List.of());

        AiDevisPrixConfirmRequest.Item item = new AiDevisPrixConfirmRequest.Item();
        item.setDeviceId(40L);
        item.setUnitPriceHt(new BigDecimal("10.00"));
        AiDevisPrixConfirmRequest request = new AiDevisPrixConfirmRequest();
        request.setItems(List.of(item));

        AiDevisPrixConfirmResponse res = devicePrixService.confirmDevisPrices(90L, request, "admin");

        assertThat(res.getConfirmedCount()).isEqualTo(1);
        verify(alerteRepository, never()).save(any());
    }

    @Test
    void confirmDevisPrices_skipsDeviceOutsideOrder() {
        Device device = TestFixtures.device();
        Commande commande = validatedCommande(device);
        when(commandeRepository.findByIdWithRelations(90L, 100L)).thenReturn(Optional.of(commande));
        when(alerteRepository.findByAtelierIdAndStatusOrderByCreatedAtDesc(100L, PrixAlerteStatus.OPEN))
                .thenReturn(List.of());

        AiDevisPrixConfirmRequest.Item item = new AiDevisPrixConfirmRequest.Item();
        item.setDeviceId(999L);
        item.setUnitPriceHt(new BigDecimal("10.00"));
        AiDevisPrixConfirmRequest request = new AiDevisPrixConfirmRequest();
        request.setItems(List.of(item));

        AiDevisPrixConfirmResponse res = devicePrixService.confirmDevisPrices(90L, request, "admin");

        assertThat(res.getConfirmedCount()).isZero();
        assertThat(res.getErrors()).isNotEmpty();
    }

    @Test
    void listAlertes_withoutStatus_listsAll() {
        when(alerteRepository.findByAtelierIdOrderByCreatedAtDesc(100L)).thenReturn(List.of());
        assertThat(devicePrixService.listAlertes(null)).isEmpty();
        assertThat(devicePrixService.listAlertes("  ")).isEmpty();
    }

    @Test
    void analyzeDevisPrices_rejectsPendingOrder() {
        Device device = TestFixtures.device();
        Commande commande = validatedCommande(device);
        commande.setStatus(OrderStatuses.PENDING);
        when(commandeRepository.findByIdWithRelations(90L, 100L)).thenReturn(Optional.of(commande));
        MockMultipartFile file = new MockMultipartFile(
                "file", "devis.pdf", "application/pdf", PDF_BYTES);
        assertThatThrownBy(() -> devicePrixService.analyzeDevisPrices(90L, file))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void confirmDevisPrices_enrichesAlerteWithAi() {
        Device device = TestFixtures.device();
        Commande commande = validatedCommande(device);
        when(commandeRepository.findByIdWithRelations(90L, 100L)).thenReturn(Optional.of(commande));
        when(deviceRepository.findByIdWithRelations(40L, 100L)).thenReturn(Optional.of(device));
        when(observationRepository
                .findTop20ByDeviceIdAndAtelierIdAndInvalidatedFalseOrderByObservedAtDesc(40L, 100L))
                .thenReturn(List.of(
                        DevicePrixObservation.builder().unitPriceHt(new BigDecimal("10.00")).build(),
                        DevicePrixObservation.builder().unitPriceHt(new BigDecimal("9.00")).build()
                ));
        when(observationRepository.save(any())).thenAnswer(inv -> {
            DevicePrixObservation o = inv.getArgument(0);
            o.setId(77L);
            return o;
        });
        when(deviceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        DevicePrixAlerte savedAlerte = DevicePrixAlerte.builder()
                .id(88L)
                .device(device)
                .observation(DevicePrixObservation.builder().id(77L).unitPriceHt(new BigDecimal("20")).build())
                .severity(PrixAlerteSeverity.ALERT)
                .status(PrixAlerteStatus.OPEN)
                .createdAt(LocalDateTime.now())
                .build();
        when(alerteRepository.save(any())).thenAnswer(inv -> {
            DevicePrixAlerte a = inv.getArgument(0);
            a.setId(88L);
            return a;
        });
        when(aiAssistantService.isEnabled()).thenReturn(true);
        when(aiAssistantService.analyzePrixIncoherences(anyList()))
                .thenReturn(List.of(com.devicemanager.dto.AiPrixIncoherenceResult.builder()
                        .deviceId(40L)
                        .severity("ALERT")
                        .summary("Hausse anormale")
                        .reasons(List.of("x2"))
                        .build()));
        when(alerteRepository.findByDeviceIdAndAtelierIdAndStatusOrderByCreatedAtDesc(
                40L, 100L, PrixAlerteStatus.OPEN))
                .thenReturn(List.of(savedAlerte));
        when(alerteRepository.findByAtelierIdAndStatusOrderByCreatedAtDesc(100L, PrixAlerteStatus.OPEN))
                .thenReturn(List.of(savedAlerte));

        AiDevisPrixConfirmRequest.Item item = new AiDevisPrixConfirmRequest.Item();
        item.setDeviceId(40L);
        item.setUnitPriceHt(new BigDecimal("20.00"));
        AiDevisPrixConfirmRequest request = new AiDevisPrixConfirmRequest();
        request.setItems(List.of(item));

        AiDevisPrixConfirmResponse res = devicePrixService.confirmDevisPrices(90L, request, "admin");

        assertThat(res.getConfirmedCount()).isEqualTo(1);
        verify(aiAssistantService).analyzePrixIncoherences(anyList());
        verify(alerteRepository, atLeastOnce()).save(argThat(a ->
                a.getAiSummary() != null && a.getAiSummary().contains("Hausse")));
    }

    @Test
    void severityFromSignals_nullOrEmpty_isNull() {
        assertThat(DevicePrixService.severityFromSignals(null)).isNull();
        assertThat(DevicePrixService.severityFromSignals(List.of())).isNull();
        assertThat(DevicePrixService.severityFromSignals(List.of("NO_BASELINE"))).isNull();
    }

    @Test
    void dismiss_updatesStatus() {
        Device device = TestFixtures.device();
        DevicePrixAlerte alerte = DevicePrixAlerte.builder()
                .id(6L)
                .device(device)
                .observation(DevicePrixObservation.builder().id(2L).unitPriceHt(BigDecimal.ONE).build())
                .severity(PrixAlerteSeverity.ALERT)
                .status(PrixAlerteStatus.OPEN)
                .createdAt(LocalDateTime.now())
                .build();
        when(alerteRepository.findByIdAndAtelierId(6L, 100L)).thenReturn(Optional.of(alerte));
        when(alerteRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        assertThat(devicePrixService.dismiss(6L, "admin").getStatus()).isEqualTo("DISMISSED");
    }

    private static Commande validatedCommande(Device device) {
        Commande commande = Commande.builder()
                .id(90L)
                .status(OrderStatuses.VALIDATED)
                .atelier(TestFixtures.atelier())
                .devisUploadedAt(LocalDateTime.of(2026, 3, 1, 12, 0))
                .lignes(new ArrayList<>())
                .build();
        commande.addLigne(CommandeLigne.builder()
                .device(device)
                .quantite(2)
                .build());
        return commande;
    }
}
