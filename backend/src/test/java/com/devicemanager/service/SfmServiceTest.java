package com.devicemanager.service;

import com.devicemanager.dto.SfmRequest;
import com.devicemanager.dto.SfmResponse;
import com.devicemanager.entity.MarqueMas;
import com.devicemanager.entity.Sfm;
import com.devicemanager.repository.MarqueMasRepository;
import com.devicemanager.repository.SfmRepository;
import com.devicemanager.support.TestFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SfmServiceTest {

    private static final Logger log = LoggerFactory.getLogger(SfmServiceTest.class);

    @Mock private SfmRepository sfmRepository;
    @Mock private MarqueMasRepository marqueMasRepository;
    @Mock private AtelierService atelierService;
    @InjectMocks private SfmService sfmService;

    @BeforeEach
    void setUp() {
        lenient().when(atelierService.requireCurrentAtelier()).thenReturn(TestFixtures.atelier());
    }

    @Test
    void create_withContactAndMarques_succeeds() {
        log.info("Test create SFM multi-marques");
        when(sfmRepository.existsByNomIgnoreCaseAndAtelierId("SFM Est", 100L)).thenReturn(false);
        when(marqueMasRepository.findAllById(anyCollection())).thenReturn(List.of(TestFixtures.marque()));
        when(sfmRepository.save(any(Sfm.class))).thenAnswer(inv -> {
            Sfm s = inv.getArgument(0);
            s.setId(31L);
            return s;
        });

        SfmRequest.SfmContactRequest contact = new SfmRequest.SfmContactRequest();
        contact.setNom(" Alice ");
        contact.setTelephone(" 0611223344 ");
        contact.setEmail(" alice@example.com ");

        SfmRequest request = new SfmRequest();
        request.setNom(" SFM Est ");
        request.setContacts(List.of(contact));
        request.setMarqueIds(List.of(5L));

        SfmResponse response = sfmService.create(request);

        assertThat(response.getNom()).isEqualTo("SFM Est");
        assertThat(response.getResponsable()).isEqualTo("Alice");
        assertThat(response.getContacts()).hasSize(1);
        assertThat(response.getMarqueIds()).containsExactly(5L);
    }

    @Test
    void create_rejectsDuplicateNom() {
        when(sfmRepository.existsByNomIgnoreCaseAndAtelierId("SFM Nord", 100L)).thenReturn(true);

        SfmRequest.SfmContactRequest contact = new SfmRequest.SfmContactRequest();
        contact.setNom("Bob");
        contact.setTelephone("0600000000");
        contact.setEmail("bob@example.com");

        SfmRequest request = new SfmRequest();
        request.setNom("SFM Nord");
        request.setContacts(List.of(contact));
        request.setMarqueIds(List.of(5L));

        assertThatThrownBy(() -> sfmService.create(request))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    ResponseStatusException rse = (ResponseStatusException) ex;
                    assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(rse.getReason()).isEqualTo("Nom SFM déjà utilisé dans cet atelier");
                });
    }

    @Test
    void create_requiresAtLeastOneContact() {
        when(sfmRepository.existsByNomIgnoreCaseAndAtelierId("SFM Vide", 100L)).thenReturn(false);

        SfmRequest request = new SfmRequest();
        request.setNom("SFM Vide");
        request.setContacts(List.of());
        request.setMarqueIds(List.of(5L));

        assertThatThrownBy(() -> sfmService.create(request))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getReason())
                .isEqualTo("Ajoutez au moins un contact SFM");
    }

    @Test
    void create_requiresAtLeastOneMarque() {
        when(sfmRepository.existsByNomIgnoreCaseAndAtelierId("SFM Vide", 100L)).thenReturn(false);

        SfmRequest.SfmContactRequest contact = new SfmRequest.SfmContactRequest();
        contact.setNom("Bob");
        contact.setTelephone("0600000000");
        contact.setEmail("bob@example.com");

        SfmRequest request = new SfmRequest();
        request.setNom("SFM Vide");
        request.setContacts(List.of(contact));
        request.setMarqueIds(List.of());

        assertThatThrownBy(() -> sfmService.create(request))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getReason())
                .isEqualTo("Sélectionnez au moins une marque");
    }

    @Test
    void findById_notFound() {
        when(sfmRepository.findByIdWithContacts(1L, 100L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> sfmService.findById(1L))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getReason())
                .isEqualTo("SFM introuvable");
    }

    @Test
    void findAll_withQuery_searches() {
        Sfm sfm = TestFixtures.sfm();
        sfm.setMarques(new HashSet<>(List.of(TestFixtures.marque())));
        when(sfmRepository.search(100L, "Nord")).thenReturn(List.of(sfm));

        assertThat(sfmService.findAll(" Nord ")).hasSize(1);
        verify(sfmRepository).search(100L, "Nord");
    }
}
