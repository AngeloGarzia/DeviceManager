package com.devicemanager.service;

import com.devicemanager.dto.MarqueMasRequest;
import com.devicemanager.dto.MasRequest;
import com.devicemanager.dto.MasResponse;
import com.devicemanager.entity.MarqueMas;
import com.devicemanager.entity.Mas;
import com.devicemanager.repository.MarqueMasRepository;
import com.devicemanager.repository.MasRepository;
import com.devicemanager.support.TestFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MasServiceTest {

    private static final Logger log = LoggerFactory.getLogger(MasServiceTest.class);

    @Mock private MasRepository masRepository;
    @Mock private MarqueMasRepository marqueMasRepository;
    @Mock private AtelierService atelierService;
    @InjectMocks private MasService masService;

    @BeforeEach
    void setUp() {
        lenient().when(atelierService.requireCurrentAtelier()).thenReturn(TestFixtures.atelier());
    }

    @Test
    void toCode_normalizesLabel() {
        assertThat(MasService.toCode(" Novomatic Élite ")).isEqualTo("NOVOMATIC_ELITE");
        assertThat(MasService.toCode("@@@")).isEqualTo("MARQUE");
    }

    @Test
    void create_persistsUniqueNumero() {
        log.info("Test create MAS");
        when(masRepository.existsByNumeroIgnoreCaseAndAtelierId("MAS-100", 100L)).thenReturn(false);
        when(marqueMasRepository.findById(5L)).thenReturn(Optional.of(TestFixtures.marque()));
        when(masRepository.save(any(Mas.class))).thenAnswer(inv -> {
            Mas m = inv.getArgument(0);
            m.setId(99L);
            return m;
        });

        MasRequest request = new MasRequest();
        request.setNumero(" MAS-100 ");
        request.setMarqueId(5L);
        request.setUtilise(true);

        MasResponse response = masService.create(request);

        assertThat(response.getNumero()).isEqualTo("MAS-100");
        assertThat(response.getMarqueLabel()).isEqualTo("Novomatic");
        verify(masRepository).save(any(Mas.class));
    }

    @Test
    void create_rejectsDuplicateNumero() {
        when(masRepository.existsByNumeroIgnoreCaseAndAtelierId("MAS-001", 100L)).thenReturn(true);

        MasRequest request = new MasRequest();
        request.setNumero("MAS-001");
        request.setMarqueId(5L);
        request.setUtilise(true);

        assertThatThrownBy(() -> masService.create(request))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    ResponseStatusException rse = (ResponseStatusException) ex;
                    assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(rse.getReason()).isEqualTo("Numéro MAS déjà utilisé dans cet atelier");
                });
    }

    @Test
    void createMarque_generatesUniqueCode() {
        when(marqueMasRepository.existsByLabelIgnoreCase("Aristocrat")).thenReturn(false);
        when(marqueMasRepository.existsByCodeIgnoreCase("ARISTOCRAT")).thenReturn(true);
        when(marqueMasRepository.existsByCodeIgnoreCase("ARISTOCRAT_2")).thenReturn(false);
        when(marqueMasRepository.save(any(MarqueMas.class))).thenAnswer(inv -> {
            MarqueMas m = inv.getArgument(0);
            m.setId(7L);
            return m;
        });

        MarqueMasRequest request = new MarqueMasRequest();
        request.setLabel(" Aristocrat ");
        var response = masService.createMarque(request);

        ArgumentCaptor<MarqueMas> captor = ArgumentCaptor.forClass(MarqueMas.class);
        verify(marqueMasRepository).save(captor.capture());
        assertThat(captor.getValue().getCode()).isEqualTo("ARISTOCRAT_2");
        assertThat(response.getLabel()).isEqualTo("Aristocrat");
    }

    @Test
    void findById_notFound() {
        when(masRepository.findByIdAndAtelierId(1L, 100L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> masService.findById(1L))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getReason())
                .isEqualTo("MAS introuvable");
    }

    @Test
    void findAll_withoutQuery_listsAtelier() {
        when(masRepository.findAllByAtelierId(100L)).thenReturn(List.of(TestFixtures.mas()));

        assertThat(masService.findAll(null)).hasSize(1);
        verify(masRepository).findAllByAtelierId(100L);
    }

    @Test
    void delete_removesEntity() {
        when(masRepository.findByIdAndAtelierId(20L, 100L)).thenReturn(Optional.of(TestFixtures.mas()));

        masService.delete(20L);

        verify(masRepository).delete(any(Mas.class));
    }
}
