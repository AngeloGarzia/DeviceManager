package com.devicemanager.service;

import com.devicemanager.dto.PublicMasReglesResponse;
import com.devicemanager.entity.MarqueMas;
import com.devicemanager.entity.Mas;
import com.devicemanager.entity.RegleJeux;
import com.devicemanager.repository.MasRepository;
import com.devicemanager.repository.RegleJeuxRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PublicMasReglesServiceTest {

    @Mock private MasRepository masRepository;
    @Mock private RegleJeuxRepository regleJeuxRepository;
    @Mock private StorageService storageService;
    @InjectMocks private PublicMasReglesService service;

    @Test
    void findByToken_returnsPublicView() {
        RegleJeux regle = RegleJeux.builder()
                .id(7L)
                .code("RJ1")
                .label("Poker")
                .description("Règle poker")
                .fileKey("k")
                .fileUrl("/u")
                .originalName("poker.pdf")
                .contentType("application/pdf")
                .build();
        Mas mas = Mas.builder()
                .id(1L)
                .numero("MAS-001")
                .publicAccessToken("tok123")
                .marque(MarqueMas.builder().id(2L).code("NOV").label("Novomatic").build())
                .reglesJeux(new HashSet<>(Set.of(regle)))
                .build();
        when(masRepository.findByPublicAccessTokenWithRegles("tok123")).thenReturn(Optional.of(mas));

        PublicMasReglesResponse response = service.findByToken("tok123");

        assertThat(response.getMasNumero()).isEqualTo("MAS-001");
        assertThat(response.getMarqueLabel()).isEqualTo("Novomatic");
        assertThat(response.getRegles()).hasSize(1);
        assertThat(response.getRegles().getFirst().getLabel()).isEqualTo("Poker");
    }

    @Test
    void findByToken_unknown_throws404() {
        when(masRepository.findByPublicAccessTokenWithRegles("bad")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findByToken("bad"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("404");
    }
}
