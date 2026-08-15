package com.devicemanager.service;

import com.devicemanager.dto.FitFromMasRequest;
import com.devicemanager.dto.FitLigneRequest;
import com.devicemanager.dto.FitResponse;
import com.devicemanager.entity.Atelier;
import com.devicemanager.entity.Fit;
import com.devicemanager.entity.FitLigne;
import com.devicemanager.entity.Intervention;
import com.devicemanager.entity.Mas;
import com.devicemanager.repository.DenoRepository;
import com.devicemanager.repository.FitLigneRepository;
import com.devicemanager.repository.FitRepository;
import com.devicemanager.repository.MasRepository;
import com.devicemanager.support.TestFixtures;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FitServiceTest {

    private static final String SIG_ADMIN =
            "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==";
    private static final String SIG_TECH =
            "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg==";

    @Mock private FitRepository fitRepository;
    @Mock private FitLigneRepository fitLigneRepository;
    @Mock private MasRepository masRepository;
    @Mock private DenoRepository denoRepository;
    @Mock private AtelierService atelierService;
    @InjectMocks private FitService fitService;

    @Test
    void ensureForMas_createsFitWhenMissing() {
        Atelier atelier = TestFixtures.atelier();
        Mas mas = TestFixtures.mas();
        when(atelierService.requireCurrentAtelier()).thenReturn(atelier);
        when(masRepository.findByIdAndAtelierId(mas.getId(), atelier.getId())).thenReturn(Optional.of(mas));
        when(fitRepository.findByAtelierIdAndMasId(atelier.getId(), mas.getId())).thenReturn(Optional.empty());
        when(fitRepository.findByAtelierIdAndNumeroMachineCasinoIgnoreCase(atelier.getId(), mas.getNumero()))
                .thenReturn(Optional.empty());
        when(fitRepository.saveAndFlush(any(Fit.class))).thenAnswer(inv -> {
            Fit f = inv.getArgument(0);
            f.setId(7L);
            return f;
        });
        when(fitRepository.findByIdAndAtelierId(7L, atelier.getId())).thenAnswer(inv -> {
            Fit f = Fit.builder()
                    .id(7L)
                    .atelier(atelier)
                    .mas(mas)
                    .numeroMachineCasino(mas.getNumero())
                    .lignes(new ArrayList<>())
                    .build();
            return Optional.of(f);
        });

        FitFromMasRequest request = new FitFromMasRequest();
        request.setMasId(mas.getId());
        FitResponse response = fitService.ensureForMas(request);

        assertThat(response.getId()).isEqualTo(7L);
        assertThat(response.getMasId()).isEqualTo(mas.getId());
        assertThat(response.getNumeroMachineCasino()).isEqualTo(mas.getNumero());
    }

    @Test
    void addLigne_rejectsMissingSignature() {
        Atelier atelier = TestFixtures.atelier();
        Fit fit = Fit.builder()
                .id(1L)
                .atelier(atelier)
                .numeroMachineCasino("M-1")
                .lignes(new ArrayList<>())
                .build();
        when(atelierService.requireCurrentAtelier()).thenReturn(atelier);
        when(fitRepository.findByIdAndAtelierId(1L, atelier.getId())).thenReturn(Optional.of(fit));

        FitLigneRequest request = new FitLigneRequest();
        request.setDateOperation(LocalDate.of(2026, 8, 15));
        request.setMotifNatureOperations("Changement lecteur");
        request.setSignatureAdmin(SIG_ADMIN);
        request.setSignatureTechnicien("not-an-image");

        assertThatThrownBy(() -> fitService.addLigne(1L, request))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getReason())
                .asString()
                .contains("technicien");
    }

    @Test
    void appendFromIntervention_writesSignedLigne() {
        Atelier atelier = TestFixtures.atelier();
        Mas mas = TestFixtures.mas();
        Intervention intervention = Intervention.builder()
                .id(55L)
                .numero("BI-100-2026-00001")
                .dateIntervention(LocalDateTime.of(2026, 8, 15, 10, 0))
                .atelier(atelier)
                .mas(mas)
                .motif("Panne")
                .travaux("Remplacement")
                .lignes(new ArrayList<>())
                .build();
        when(fitRepository.findByAtelierIdAndMasId(atelier.getId(), mas.getId())).thenReturn(Optional.empty());
        when(fitRepository.findByAtelierIdAndNumeroMachineCasinoIgnoreCase(atelier.getId(), mas.getNumero()))
                .thenReturn(Optional.empty());
        when(fitRepository.saveAndFlush(any(Fit.class))).thenAnswer(inv -> {
            Fit f = inv.getArgument(0);
            if (f.getId() == null) {
                f.setId(9L);
            }
            if (f.getLignes() == null) {
                f.setLignes(new ArrayList<>());
            }
            return f;
        });
        AtomicLong ligneSeq = new AtomicLong(100);
        when(fitLigneRepository.saveAndFlush(any(FitLigne.class))).thenAnswer(inv -> {
            FitLigne l = inv.getArgument(0);
            l.setId(ligneSeq.getAndIncrement());
            return l;
        });

        fitService.appendFromIntervention(intervention, SIG_ADMIN, SIG_TECH, "Admin", "Tech");

        ArgumentCaptor<FitLigne> captor = ArgumentCaptor.forClass(FitLigne.class);
        verify(fitLigneRepository).saveAndFlush(captor.capture());
        FitLigne saved = captor.getValue();
        assertThat(saved.getSignatureAdmin()).startsWith("data:image/");
        assertThat(saved.getIntervention()).isEqualTo(intervention);
        assertThat(saved.getFit()).isNotNull();
        assertThat(saved.getFit().getId()).isEqualTo(9L);
    }
}
