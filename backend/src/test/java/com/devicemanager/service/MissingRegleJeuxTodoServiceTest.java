package com.devicemanager.service;

import com.devicemanager.entity.Atelier;
import com.devicemanager.entity.Mas;
import com.devicemanager.entity.RegleJeux;
import com.devicemanager.entity.TodoTache;
import com.devicemanager.entity.TodoTacheStatut;
import com.devicemanager.repository.MasRepository;
import com.devicemanager.repository.TodoTacheRepository;
import com.devicemanager.support.TestFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashSet;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MissingRegleJeuxTodoServiceTest {

    @Mock private MasRepository masRepository;
    @Mock private TodoTacheRepository todoTacheRepository;
    @Mock private AtelierService atelierService;
    @Mock private AtelierMemoirePublisher atelierMemoirePublisher;
    @InjectMocks private MissingRegleJeuxTodoService service;

    private Atelier atelier;

    @BeforeEach
    void setUp() {
        atelier = TestFixtures.atelier();
        lenient().when(atelierService.requireCurrentAtelier()).thenReturn(atelier);
    }

    @Test
    void sync_createsHighPriorityTodoWhenMasMissingRules() {
        Mas mas1 = TestFixtures.mas();
        mas1.setNumero("MAS-001");
        mas1.setReglesJeux(new HashSet<>());
        Mas mas2 = TestFixtures.mas();
        mas2.setId(21L);
        mas2.setNumero("MAS-002");
        mas2.setReglesJeux(new HashSet<>());
        when(masRepository.findAllByAtelierId(100L)).thenReturn(List.of(mas1, mas2));
        when(todoTacheRepository.findByAtelierIdAndStatutInAndDescriptionContaining(
                eq(100L), any(), eq(MissingRegleJeuxTodoService.AUTO_MARKER)))
                .thenReturn(List.of());
        when(todoTacheRepository.save(any(TodoTache.class))).thenAnswer(inv -> inv.getArgument(0));

        service.syncForCurrentAtelier();

        ArgumentCaptor<TodoTache> captor = ArgumentCaptor.forClass(TodoTache.class);
        verify(todoTacheRepository).save(captor.capture());
        TodoTache saved = captor.getValue();
        assertThat(saved.getTitre()).isEqualTo("Il manque 2 règles de jeux");
        assertThat(saved.getSeverite()).isEqualTo("HIGH");
        assertThat(saved.getDescription()).contains(MissingRegleJeuxTodoService.AUTO_MARKER);
        assertThat(saved.getDescription()).contains("MAS-001").contains("MAS-002");
        assertThat(saved.getCreatedByUsername()).isEqualTo("system");
    }

    @Test
    void sync_cancelsTodoWhenAllMasHaveRules() {
        Mas mas = TestFixtures.mas();
        mas.setReglesJeux(new HashSet<>(List.of(RegleJeux.builder()
                .id(1L)
                .code("BOOK")
                .label("Book of Ra")
                .fileKey("k")
                .fileUrl("/u")
                .build())));
        when(masRepository.findAllByAtelierId(100L)).thenReturn(List.of(mas));
        TodoTache existing = TodoTache.builder()
                .id(9L)
                .atelier(atelier)
                .titre("Il manque 1 règle de jeux")
                .description(MissingRegleJeuxTodoService.AUTO_MARKER + "\n- MAS-001")
                .statut(TodoTacheStatut.OPEN)
                .severite("HIGH")
                .createdByUsername("system")
                .build();
        when(todoTacheRepository.findByAtelierIdAndStatutInAndDescriptionContaining(
                eq(100L), any(), eq(MissingRegleJeuxTodoService.AUTO_MARKER)))
                .thenReturn(List.of(existing));

        service.syncForCurrentAtelier();

        assertThat(existing.getStatut()).isEqualTo(TodoTacheStatut.CANCELLED);
        verify(todoTacheRepository).save(existing);
    }

    @Test
    void sync_doesNothingWhenNoMissingAndNoTodo() {
        Mas mas = TestFixtures.mas();
        mas.setReglesJeux(new HashSet<>(List.of(RegleJeux.builder()
                .id(1L)
                .code("BOOK")
                .label("Book of Ra")
                .fileKey("k")
                .fileUrl("/u")
                .build())));
        when(masRepository.findAllByAtelierId(100L)).thenReturn(List.of(mas));
        when(todoTacheRepository.findByAtelierIdAndStatutInAndDescriptionContaining(
                eq(100L), any(), eq(MissingRegleJeuxTodoService.AUTO_MARKER)))
                .thenReturn(List.of());

        service.syncForCurrentAtelier();

        verify(todoTacheRepository, never()).save(any());
    }

    @Test
    void buildTitre_pluralizes() {
        assertThat(MissingRegleJeuxTodoService.buildTitre(1)).isEqualTo("Il manque 1 règle de jeux");
        assertThat(MissingRegleJeuxTodoService.buildTitre(3)).isEqualTo("Il manque 3 règles de jeux");
    }
}
