package com.devicemanager.service;

import com.devicemanager.dto.VisiteQuadriObligationResponse;
import com.devicemanager.dto.VisiteQuadriRequest;
import com.devicemanager.entity.MarqueMas;
import com.devicemanager.entity.Sfm;
import com.devicemanager.entity.VisiteQuadritrimestrelle;
import com.devicemanager.repository.SfmRepository;
import com.devicemanager.repository.VisiteQuadriRepository;
import com.devicemanager.support.TestFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class VisiteQuadriServiceTest {

    @Mock private VisiteQuadriRepository visiteQuadriRepository;
    @Mock private SfmRepository sfmRepository;
    @Mock private AtelierService atelierService;
    @InjectMocks private VisiteQuadriService service;

    @BeforeEach
    void setUp() {
        lenient().when(atelierService.requireCurrentAtelier()).thenReturn(TestFixtures.atelier());
    }

    @Test
    void toObligation_neverVisited_isOverdue() {
        LocalDate today = LocalDate.of(2026, 8, 16);
        VisiteQuadriObligationResponse o = VisiteQuadriService.toObligation(
                sfm(1L, "SFM A"), marque(10L, "Novomatic"), null, today);

        assertThat(o.getLevel()).isEqualTo(VisiteQuadriService.LEVEL_OVERDUE);
        assertThat(o.getDueDate()).isEqualTo(today);
        assertThat(o.getDaysRemaining()).isZero();
        assertThat(o.getLastVisitDate()).isNull();
    }

    @Test
    void toObligation_withinSevenDays_isWarn() {
        LocalDate today = LocalDate.of(2026, 8, 16);
        LocalDate due = today.plusDays(5);
        LocalDate last = due.minusMonths(4);
        VisiteQuadriObligationResponse o = VisiteQuadriService.toObligation(
                sfm(1L, "SFM A"), marque(10L, "Novomatic"), last, today);

        assertThat(o.getDueDate()).isEqualTo(due);
        assertThat(o.getDaysRemaining()).isEqualTo(5);
        assertThat(o.getLevel()).isEqualTo(VisiteQuadriService.LEVEL_WARN);
    }

    @Test
    void toObligation_moreThanSevenDays_isOk() {
        LocalDate today = LocalDate.of(2026, 8, 16);
        LocalDate last = today.minusMonths(4).plusDays(20);
        VisiteQuadriObligationResponse o = VisiteQuadriService.toObligation(
                sfm(1L, "SFM A"), marque(10L, "Novomatic"), last, today);

        assertThat(o.getDueDate()).isEqualTo(last.plusMonths(4));
        assertThat(o.getDaysRemaining()).isGreaterThan(VisiteQuadriService.WARN_DAYS);
        assertThat(o.getLevel()).isEqualTo(VisiteQuadriService.LEVEL_OK);
    }

    @Test
    void toObligation_pastDue_isOverdue() {
        LocalDate today = LocalDate.of(2026, 8, 16);
        LocalDate last = today.minusMonths(4).minusDays(10);
        VisiteQuadriObligationResponse o = VisiteQuadriService.toObligation(
                sfm(1L, "SFM A"), marque(10L, "Novomatic"), last, today);

        assertThat(o.getDaysRemaining()).isNegative();
        assertThat(o.getLevel()).isEqualTo(VisiteQuadriService.LEVEL_OVERDUE);
    }

    @Test
    void create_rejectsMarqueOutsideSfmCompetences() {
        Sfm sfm = sfm(5L, "SFM Est");
        sfm.setMarques(Set.of(marque(1L, "Novomatic")));
        when(sfmRepository.findByIdWithMarques(5L, 100L)).thenReturn(Optional.of(sfm));

        VisiteQuadriRequest request = new VisiteQuadriRequest();
        request.setSfmId(5L);
        request.setMarqueId(99L);
        request.setDateVisite(LocalDate.of(2026, 8, 1));

        assertThatThrownBy(() -> service.create(request, "admin"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    ResponseStatusException rse = (ResponseStatusException) ex;
                    assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(rse.getReason()).containsIgnoringCase("compétence");
                });
        verify(visiteQuadriRepository, never()).save(any());
    }

    @Test
    void create_persistsWhenMarqueIsCompetence() {
        MarqueMas marque = marque(1L, "Novomatic");
        Sfm sfm = sfm(5L, "SFM Est");
        sfm.setMarques(Set.of(marque));
        when(sfmRepository.findByIdWithMarques(5L, 100L)).thenReturn(Optional.of(sfm));
        when(visiteQuadriRepository.save(any(VisiteQuadritrimestrelle.class))).thenAnswer(inv -> {
            VisiteQuadritrimestrelle v = inv.getArgument(0);
            v.setId(42L);
            return v;
        });

        VisiteQuadriRequest request = new VisiteQuadriRequest();
        request.setSfmId(5L);
        request.setMarqueId(1L);
        request.setDateVisite(LocalDate.of(2026, 8, 10));
        request.setNotes("OK");

        var response = service.create(request, "admin");

        ArgumentCaptor<VisiteQuadritrimestrelle> captor =
                ArgumentCaptor.forClass(VisiteQuadritrimestrelle.class);
        verify(visiteQuadriRepository).save(captor.capture());
        assertThat(captor.getValue().getDateVisite()).isEqualTo(LocalDate.of(2026, 8, 10));
        assertThat(captor.getValue().getCreatedBy()).isEqualTo("admin");
        assertThat(response.getId()).isEqualTo(42L);
        assertThat(response.getMarqueLabel()).isEqualTo("Novomatic");
    }

    private static Sfm sfm(Long id, String nom) {
        Sfm s = new Sfm();
        s.setId(id);
        s.setNom(nom);
        return s;
    }

    private static MarqueMas marque(Long id, String label) {
        return MarqueMas.builder().id(id).code(label.toUpperCase()).label(label).build();
    }
}
