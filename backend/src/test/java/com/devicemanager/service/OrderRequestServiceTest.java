package com.devicemanager.service;

import com.devicemanager.dto.OrderRequestDto;
import com.devicemanager.dto.OrderRequestResponse;
import com.devicemanager.entity.Commande;
import com.devicemanager.entity.CommandeLigne;
import com.devicemanager.repository.CommandeRepository;
import com.devicemanager.repository.DeviceRepository;
import com.devicemanager.repository.UserRepository;
import com.devicemanager.security.OrderStatuses;
import com.devicemanager.security.Roles;
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

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderRequestServiceTest {

    private static final byte[] PDF_BYTES = "%PDF-1.4 minimal".getBytes();

    @Mock private CommandeRepository commandeRepository;
    @Mock private DeviceRepository deviceRepository;
    @Mock private UserRepository userRepository;
    @Mock private MailService mailService;
    @Mock private AtelierService atelierService;
    @Mock private StockMouvementService stockMouvementService;
    @Mock private StorageService storageService;
    @Mock private AiAssistantService aiAssistantService;
    @Mock private DeviceService deviceService;
    @InjectMocks private OrderRequestService orderRequestService;

    @BeforeEach
    void setUp() {
        lenient().when(atelierService.requireCurrentAtelier()).thenReturn(TestFixtures.atelier());
    }

    @Test
    void create_mergesQuantitiesAndNotifiesAdminWithSfm() {
        when(userRepository.findByUsername("tech")).thenReturn(Optional.of(TestFixtures.user("tech", Roles.TECHNICIEN)));
        when(deviceRepository.findByIdWithRelations(40L, 100L)).thenReturn(Optional.of(TestFixtures.device()));
        when(commandeRepository.save(any(Commande.class))).thenAnswer(inv -> {
            Commande c = inv.getArgument(0);
            c.setId(70L);
            return c;
        });

        OrderRequestDto.OrderRequestLineDto line1 = new OrderRequestDto.OrderRequestLineDto();
        line1.setDeviceId(40L);
        line1.setQuantite(2);
        OrderRequestDto.OrderRequestLineDto line2 = new OrderRequestDto.OrderRequestLineDto();
        line2.setDeviceId(40L);
        line2.setQuantite(3);

        OrderRequestDto request = new OrderRequestDto();
        request.setMessage(" Urgent ");
        request.setLignes(List.of(line1, line2));

        OrderRequestResponse response = orderRequestService.create(request, "tech");

        assertThat(response.getTotalPieces()).isEqualTo(1);
        assertThat(response.getTotalQuantite()).isEqualTo(5);
        assertThat(response.getStatus()).isEqualTo(OrderStatuses.PENDING);
        ArgumentCaptor<String> bodyCaptor = ArgumentCaptor.forClass(String.class);
        verify(mailService).sendOrderRequestToAdmin(contains("1 pièce"), bodyCaptor.capture());
        assertThat(bodyCaptor.getValue()).contains("SFM Nord").contains("Urgent").contains("validation");
    }

    @Test
    void create_rejectsEmptyLines() {
        when(userRepository.findByUsername("tech")).thenReturn(Optional.of(TestFixtures.user("tech", Roles.TECHNICIEN)));

        OrderRequestDto request = new OrderRequestDto();
        request.setMessage("msg");
        request.setLignes(List.of());

        assertThatThrownBy(() -> orderRequestService.create(request, "tech"))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getReason())
                .isEqualTo("Ajoutez au moins une pièce à la demande");
    }

    @Test
    void create_rejectsUnknownDevice() {
        when(userRepository.findByUsername("tech")).thenReturn(Optional.of(TestFixtures.user("tech", Roles.TECHNICIEN)));
        when(deviceRepository.findByIdWithRelations(999L, 100L)).thenReturn(Optional.empty());

        OrderRequestDto.OrderRequestLineDto line = new OrderRequestDto.OrderRequestLineDto();
        line.setDeviceId(999L);
        line.setQuantite(1);
        OrderRequestDto request = new OrderRequestDto();
        request.setMessage("msg");
        request.setLignes(List.of(line));

        assertThatThrownBy(() -> orderRequestService.create(request, "tech"))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getReason())
                .isEqualTo("Pièce détachée introuvable dans cet atelier.");
    }

    @Test
    void findAll_listsForAtelier() {
        when(commandeRepository.findAllWithRelationsOrderByDateDesc(100L)).thenReturn(List.of());

        assertThat(orderRequestService.findAll()).isEmpty();
    }

    @Test
    void validate_sendsOneMailPerSfmAndMarksValidated() {
        var device = TestFixtures.device();
        Commande commande = Commande.builder()
                .id(70L)
                .technicien(TestFixtures.user("tech", Roles.TECHNICIEN))
                .technicienNom("tech")
                .message("Besoin urgent")
                .dateDemande(LocalDateTime.now())
                .status(OrderStatuses.PENDING)
                .atelier(TestFixtures.atelier())
                .lignes(new ArrayList<>())
                .build();
        CommandeLigne ligne = CommandeLigne.builder().id(1L).device(device).quantite(2).build();
        commande.addLigne(ligne);

        when(commandeRepository.findByIdWithRelations(70L, 100L)).thenReturn(Optional.of(commande));
        when(commandeRepository.save(any(Commande.class))).thenAnswer(inv -> inv.getArgument(0));
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(
                TestFixtures.user("admin", Roles.ADMIN).toBuilder()
                        .prenom("Sophie")
                        .nom("Martin")
                        .email("sophie.martin@casino.local")
                        .build()));

        OrderRequestResponse response = orderRequestService.validate(70L, "admin");

        assertThat(response.getStatus()).isEqualTo(OrderStatuses.VALIDATED);
        assertThat(response.getDateValidation()).isNotNull();
        ArgumentCaptor<String> subjectCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> bodyCaptor = ArgumentCaptor.forClass(String.class);
        verify(mailService).send(eq("jean@example.com"), subjectCaptor.capture(), bodyCaptor.capture());
        assertThat(subjectCaptor.getValue()).isEqualTo("Demande de devis #70");
        assertThat(bodyCaptor.getValue())
                .contains("Pouvez-vous nous faire un devis pour les pièces détachées suivantes")
                .contains("Carte mère")
                .contains("Merci, bien à vous.")
                .contains("Sophie Martin")
                .contains("sophie.martin@casino.local")
                .contains("tech@test.local")
                .doesNotContain("Merci de traiter cette commande");
    }

    @Test
    void validate_rejectsAlreadyValidated() {
        Commande commande = Commande.builder()
                .id(71L)
                .technicien(TestFixtures.user("tech", Roles.TECHNICIEN))
                .technicienNom("tech")
                .message("x")
                .dateDemande(LocalDateTime.now())
                .status(OrderStatuses.VALIDATED)
                .atelier(TestFixtures.atelier())
                .lignes(new ArrayList<>())
                .build();
        when(commandeRepository.findByIdWithRelations(71L, 100L)).thenReturn(Optional.of(commande));

        assertThatThrownBy(() -> orderRequestService.validate(71L, "admin"))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getReason())
                .asString()
                .contains("déjà validée");
    }

    @Test
    void delete_removesPendingOrder() {
        Commande commande = Commande.builder()
                .id(72L)
                .technicien(TestFixtures.user("tech", Roles.TECHNICIEN))
                .technicienNom("tech")
                .message("x")
                .dateDemande(LocalDateTime.now())
                .status(OrderStatuses.PENDING)
                .atelier(TestFixtures.atelier())
                .lignes(new ArrayList<>())
                .build();
        when(commandeRepository.findByIdWithRelations(72L, 100L)).thenReturn(Optional.of(commande));

        orderRequestService.delete(72L, "admin");

        verify(commandeRepository).delete(commande);
    }

    @Test
    void delete_rejectsUnknownOrder() {
        when(commandeRepository.findByIdWithRelations(999L, 100L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderRequestService.delete(999L, "admin"))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getReason())
                .isEqualTo("Demande de commande introuvable");
    }

    @Test
    void update_adjustsQuantitiesWhenValidated() {
        var device = TestFixtures.device();
        device.setStock(1);
        Commande commande = Commande.builder()
                .id(80L)
                .technicien(TestFixtures.user("tech", Roles.TECHNICIEN))
                .technicienNom("tech")
                .message("msg")
                .dateDemande(LocalDateTime.now())
                .status(OrderStatuses.VALIDATED)
                .atelier(TestFixtures.atelier())
                .lignes(new ArrayList<>())
                .build();
        commande.addLigne(CommandeLigne.builder().id(1L).device(device).quantite(5).build());

        when(commandeRepository.findByIdWithRelations(80L, 100L)).thenReturn(Optional.of(commande));
        when(deviceRepository.findByIdWithRelations(40L, 100L)).thenReturn(Optional.of(device));
        when(commandeRepository.save(any(Commande.class))).thenAnswer(inv -> inv.getArgument(0));

        OrderRequestDto.OrderRequestLineDto line = new OrderRequestDto.OrderRequestLineDto();
        line.setDeviceId(40L);
        line.setQuantite(3);
        OrderRequestDto request = new OrderRequestDto();
        request.setMessage("Réception partielle");
        request.setLignes(List.of(line));

        OrderRequestResponse response = orderRequestService.update(80L, request, "admin");

        assertThat(response.getStatus()).isEqualTo(OrderStatuses.VALIDATED);
        assertThat(response.getTotalQuantite()).isEqualTo(3);
        assertThat(response.getMessage()).isEqualTo("Réception partielle");
    }

    @Test
    void receive_incrementsStockAndMarksReceived() {
        var device = TestFixtures.device();
        device.setStock(2);
        Commande commande = Commande.builder()
                .id(81L)
                .technicien(TestFixtures.user("tech", Roles.TECHNICIEN))
                .technicienNom("tech")
                .message("msg")
                .dateDemande(LocalDateTime.now())
                .status(OrderStatuses.VALIDATED)
                .atelier(TestFixtures.atelier())
                .lignes(new ArrayList<>())
                .build();
        commande.addLigne(CommandeLigne.builder().id(1L).device(device).quantite(4).build());

        when(commandeRepository.findByIdWithRelations(81L, 100L)).thenReturn(Optional.of(commande));
        when(commandeRepository.claimStatus(81L, 100L, OrderStatuses.VALIDATED, OrderStatuses.RECEIVED))
                .thenAnswer(inv -> {
                    commande.setStatus(OrderStatuses.RECEIVED);
                    return 1;
                });
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(TestFixtures.user("admin", Roles.ADMIN)));
        when(deviceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(commandeRepository.save(any(Commande.class))).thenAnswer(inv -> inv.getArgument(0));
        when(stockMouvementService.record(any(), any(), anyInt(), anyInt(), anyString(), any(), anyString()))
                .thenAnswer(inv -> null);

        OrderRequestResponse response = orderRequestService.receive(81L, null, "admin");

        assertThat(response.getStatus()).isEqualTo(OrderStatuses.RECEIVED);
        assertThat(response.getDateReception()).isNotNull();
        assertThat(device.getStock()).isEqualTo(6);
        verify(deviceRepository).save(device);
        verify(commandeRepository).claimStatus(81L, 100L, OrderStatuses.VALIDATED, OrderStatuses.RECEIVED);
        verify(stockMouvementService).record(
                eq(commande.getAtelier()),
                eq(device),
                eq(2),
                eq(6),
                eq("ORDER_RECEIVE"),
                eq(81L),
                anyString());
    }

    @Test
    void receive_rejectsPendingOrder() {
        Commande commande = Commande.builder()
                .id(82L)
                .technicien(TestFixtures.user("tech", Roles.TECHNICIEN))
                .technicienNom("tech")
                .message("x")
                .dateDemande(LocalDateTime.now())
                .status(OrderStatuses.PENDING)
                .atelier(TestFixtures.atelier())
                .lignes(new ArrayList<>())
                .build();
        when(commandeRepository.findByIdWithRelations(82L, 100L)).thenReturn(Optional.of(commande));

        assertThatThrownBy(() -> orderRequestService.receive(82L, null, "admin"))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getReason())
                .asString()
                .contains("validée");
    }

    @Test
    void delete_rejectsReceivedOrder() {
        Commande commande = Commande.builder()
                .id(83L)
                .technicien(TestFixtures.user("tech", Roles.TECHNICIEN))
                .technicienNom("tech")
                .message("x")
                .dateDemande(LocalDateTime.now())
                .status(OrderStatuses.RECEIVED)
                .atelier(TestFixtures.atelier())
                .lignes(new ArrayList<>())
                .build();
        when(commandeRepository.findByIdWithRelations(83L, 100L)).thenReturn(Optional.of(commande));

        assertThatThrownBy(() -> orderRequestService.delete(83L, "admin"))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getReason())
                .asString()
                .contains("réceptionnée");
    }

    @Test
    void attachDevis_storesPdfOnValidatedOrder() {
        Commande commande = Commande.builder()
                .id(90L)
                .technicien(TestFixtures.user("tech", Roles.TECHNICIEN))
                .technicienNom("tech")
                .message("x")
                .dateDemande(LocalDateTime.now())
                .status(OrderStatuses.VALIDATED)
                .atelier(TestFixtures.atelier())
                .lignes(new ArrayList<>())
                .build();
        when(commandeRepository.findByIdWithRelations(90L, 100L)).thenReturn(Optional.of(commande));
        when(storageService.store(any())).thenReturn(
                new StorageService.StoredObject("devis/90.pdf", "/uploads/devis-90.pdf", "application/pdf", 42L));
        when(commandeRepository.save(any(Commande.class))).thenAnswer(inv -> inv.getArgument(0));

        MockMultipartFile file = new MockMultipartFile(
                "file", "devis-sfm.pdf", "application/pdf", PDF_BYTES);

        OrderRequestResponse response = orderRequestService.attachDevis(90L, file, "admin");

        assertThat(response.getDevisOriginalName()).isEqualTo("devis-sfm.pdf");
        assertThat(response.getDevisFileUrl()).isEqualTo("/uploads/devis-90.pdf");
        assertThat(response.getDevisFileSize()).isEqualTo(42L);
        assertThat(commande.getDevisFileKey()).isEqualTo("devis/90.pdf");
        assertThat(commande.getDevisUploadedAt()).isNotNull();
    }

    @Test
    void attachDevis_rejectsPendingOrder() {
        Commande commande = Commande.builder()
                .id(91L)
                .technicien(TestFixtures.user("tech", Roles.TECHNICIEN))
                .technicienNom("tech")
                .message("x")
                .dateDemande(LocalDateTime.now())
                .status(OrderStatuses.PENDING)
                .atelier(TestFixtures.atelier())
                .lignes(new ArrayList<>())
                .build();
        when(commandeRepository.findByIdWithRelations(91L, 100L)).thenReturn(Optional.of(commande));

        MockMultipartFile file = new MockMultipartFile(
                "file", "devis.pdf", "application/pdf", PDF_BYTES);

        assertThatThrownBy(() -> orderRequestService.attachDevis(91L, file, "admin"))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getReason())
                .asString()
                .containsIgnoringCase("validation");
    }
}
