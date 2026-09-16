package com.devicemanager.service;

import com.devicemanager.dto.TodoModeleRequest;
import com.devicemanager.dto.TodoModeleResponse;
import com.devicemanager.dto.TodoTacheResponse;
import com.devicemanager.entity.TodoModele;
import com.devicemanager.repository.MasRepository;
import com.devicemanager.repository.TodoModeleRepository;
import com.devicemanager.repository.UserRepository;
import com.devicemanager.support.TestFixtures;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TodoModeleServiceTest {

    @Mock private TodoModeleRepository todoModeleRepository;
    @Mock private MasRepository masRepository;
    @Mock private UserRepository userRepository;
    @Mock private AtelierService atelierService;
    @Mock private TodoService todoService;
    @InjectMocks private TodoModeleService todoModeleService;

    @Test
    void list_returnsAtelierModeles() {
        var atelier = TestFixtures.atelier();
        TodoModele modele = TodoModele.builder()
                .id(1L)
                .atelier(atelier)
                .titre("Contrôle niveau huile")
                .severite("MEDIUM")
                .position(0)
                .createdByUsername("tech")
                .build();
        when(atelierService.requireCurrentAtelier()).thenReturn(atelier);
        when(todoModeleRepository.findAllByAtelierIdOrderByPosition(atelier.getId())).thenReturn(List.of(modele));

        List<TodoModeleResponse> list = todoModeleService.list();

        assertThat(list).hasSize(1);
        assertThat(list.get(0).getTitre()).isEqualTo("Contrôle niveau huile");
    }

    @Test
    void utiliser_createsTaskFromModele() {
        var atelier = TestFixtures.atelier();
        TodoModele modele = TodoModele.builder()
                .id(5L)
                .atelier(atelier)
                .titre("Nettoyage filtres")
                .description("Filtres cabine")
                .severite("HIGH")
                .build();
        when(atelierService.requireCurrentAtelier()).thenReturn(atelier);
        when(todoModeleRepository.findByIdAndAtelierId(5L, atelier.getId())).thenReturn(Optional.of(modele));
        when(todoService.create(any(), any())).thenReturn(
                TodoTacheResponse.builder().id(99L).titre("Nettoyage filtres").statut("OPEN").build());

        TodoTacheResponse created = todoModeleService.utiliser(5L, "tech");

        assertThat(created.getId()).isEqualTo(99L);
        verify(todoService).create(any(), org.mockito.ArgumentMatchers.eq("tech"));
    }
}
