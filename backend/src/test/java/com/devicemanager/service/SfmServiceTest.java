package com.devicemanager.service;

import com.devicemanager.dto.SfmRequest;
import com.devicemanager.dto.SfmResponse;
import com.devicemanager.entity.MarqueMas;
import com.devicemanager.entity.Sfm;
import com.devicemanager.entity.SfmContact;
import com.devicemanager.repository.MarqueMasRepository;
import com.devicemanager.repository.SfmContactRepository;
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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SfmServiceTest {

    private static final Logger log = LoggerFactory.getLogger(SfmServiceTest.class);

    @Mock private SfmRepository sfmRepository;
    @Mock private SfmContactRepository sfmContactRepository;
    @Mock private MarqueMasRepository marqueMasRepository;
    @Mock private AtelierService atelierService;
    @InjectMocks private SfmService sfmService;

    private final AtomicLong contactIds = new AtomicLong(1);

    @BeforeEach
    void setUp() {
        lenient().when(atelierService.requireCurrentAtelier()).thenReturn(TestFixtures.atelier());
        lenient().when(sfmContactRepository.save(any(SfmContact.class))).thenAnswer(inv -> {
            SfmContact c = inv.getArgument(0);
            if (c.getId() == null) {
                c.setId(contactIds.getAndIncrement());
            }
            return c;
        });
        lenient().when(sfmContactRepository.findByEmailIgnoreCase(anyString())).thenReturn(Optional.empty());
        lenient().when(sfmContactRepository.countSfmsByContactId(anyLong())).thenReturn(0L);
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
        assertThat(response.getContacts().get(0).isReceiveOrderMails()).isTrue();
        assertThat(response.getMarqueIds()).containsExactly(5L);
    }

    @Test
    void create_respectsReceiveOrderMailsFalse() {
        when(sfmRepository.existsByNomIgnoreCaseAndAtelierId("SFM Ouest", 100L)).thenReturn(false);
        when(marqueMasRepository.findAllById(anyCollection())).thenReturn(List.of(TestFixtures.marque()));
        when(sfmRepository.save(any(Sfm.class))).thenAnswer(inv -> {
            Sfm s = inv.getArgument(0);
            s.setId(32L);
            return s;
        });

        SfmRequest.SfmContactRequest contact = new SfmRequest.SfmContactRequest();
        contact.setNom("Claire");
        contact.setTelephone("0600112233");
        contact.setEmail("claire@example.com");
        contact.setReceiveOrderMails(false);

        SfmRequest request = new SfmRequest();
        request.setNom("SFM Ouest");
        request.setContacts(List.of(contact));
        request.setMarqueIds(List.of(5L));

        SfmResponse response = sfmService.create(request);

        assertThat(response.getContacts().get(0).isReceiveOrderMails()).isFalse();
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

    @Test
    void update_reattachesSameTechnicienWithoutDeletingOtherLink() {
        SfmContact tech = SfmContact.builder()
                .id(10L)
                .nom("Paul")
                .telephone("0611223344")
                .email("paul@example.com")
                .technicienSfm(true)
                .receiveOrderMails(true)
                .sfms(new HashSet<>())
                .build();

        Sfm sfmA = Sfm.builder()
                .id(1L)
                .nom("SFM A")
                .responsable("Paul")
                .telephone("0611223344")
                .email("paul@example.com")
                .contacts(new ArrayList<>(List.of(tech)))
                .marques(new HashSet<>(List.of(TestFixtures.marque())))
                .atelier(TestFixtures.atelier())
                .build();
        tech.getSfms().add(sfmA);

        when(sfmRepository.findByIdWithContacts(1L, 100L)).thenReturn(Optional.of(sfmA));
        when(sfmRepository.existsByNomIgnoreCaseAndAtelierIdAndIdNot("SFM A", 100L, 1L)).thenReturn(false);
        when(sfmContactRepository.findById(10L)).thenReturn(Optional.of(tech));
        when(marqueMasRepository.findAllById(anyCollection())).thenReturn(List.of(TestFixtures.marque()));
        when(sfmRepository.save(any(Sfm.class))).thenAnswer(inv -> inv.getArgument(0));

        SfmRequest.SfmContactRequest contactReq = new SfmRequest.SfmContactRequest();
        contactReq.setId(10L);
        contactReq.setNom("Paul");
        contactReq.setTelephone("0611223344");
        contactReq.setEmail("paul@example.com");
        contactReq.setTechnicienSfm(true);

        SfmRequest request = new SfmRequest();
        request.setNom("SFM A");
        request.setContacts(List.of(contactReq));
        request.setMarqueIds(List.of(5L));

        SfmResponse response = sfmService.update(1L, request);

        assertThat(response.getContacts()).hasSize(1);
        assertThat(response.getContacts().get(0).isTechnicienSfm()).isTrue();
        verify(sfmContactRepository, never()).delete(any());
    }
}
