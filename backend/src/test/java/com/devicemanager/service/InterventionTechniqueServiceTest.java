package com.devicemanager.service;

import com.devicemanager.dto.InterventionTechniqueRequest;
import com.devicemanager.dto.InterventionTechniqueResponse;
import com.devicemanager.entity.Atelier;
import com.devicemanager.entity.Commande;
import com.devicemanager.entity.CommandeLigne;
import com.devicemanager.entity.Device;
import com.devicemanager.entity.Fit;
import com.devicemanager.entity.FitLigne;
import com.devicemanager.entity.Intervention;
import com.devicemanager.entity.InterventionTechnique;
import com.devicemanager.entity.Mas;
import com.devicemanager.entity.User;
import com.devicemanager.repository.CommandeRepository;
import com.devicemanager.repository.InterventionRepository;
import com.devicemanager.repository.InterventionTechniqueRepository;
import com.devicemanager.repository.MasRepository;
import com.devicemanager.repository.UserRepository;
import com.devicemanager.security.Roles;
import com.devicemanager.support.TestFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InterventionTechniqueServiceTest {

    private static final String SIG_ADMIN =
            "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==";
    private static final String SIG_TECH =
            "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg==";

    @Mock private InterventionTechniqueRepository interventionTechniqueRepository;
    @Mock private MasRepository masRepository;
    @Mock private UserRepository userRepository;
    @Mock private CommandeRepository commandeRepository;
    @Mock private InterventionRepository interventionRepository;
    @Mock private AtelierService atelierService;
    @Mock private FitService fitService;
    @InjectMocks private InterventionTechniqueService service;

    private Atelier atelier;
    private Mas mas;
    private User tech;

    @BeforeEach
    void setUp() {
        atelier = TestFixtures.atelier();
        mas = TestFixtures.mas();
        tech = TestFixtures.user("tech", Roles.TECHNICIEN);
        when(atelierService.requireCurrentAtelier()).thenReturn(atelier);
    }

    @Test
    void create_oneMasWithoutLinks() {
        when(userRepository.findByUsername("tech")).thenReturn(Optional.of(tech));
        when(masRepository.findByIdAndAtelierId(mas.getId(), atelier.getId())).thenReturn(Optional.of(mas));
        AtomicLong seq = new AtomicLong(1);
        when(interventionTechniqueRepository.save(any(InterventionTechnique.class))).thenAnswer(inv -> {
            InterventionTechnique e = inv.getArgument(0);
            e.setId(seq.getAndIncrement());
            return e;
        });

        InterventionTechniqueRequest request = baseRequest(List.of(mas.getId()));
        List<InterventionTechniqueResponse> created = service.create(request, "tech");

        assertThat(created).hasSize(1);
        assertThat(created.getFirst().getMasId()).isEqualTo(mas.getId());
        assertThat(created.getFirst().getMotif()).isEqualTo("Panne lecteur");
        assertThat(created.getFirst().getVisiteGroupeId()).isNotBlank();
    }

    @Test
    void create_multiMasSharesVisiteGroupeId() {
        Mas mas2 = Mas.builder()
                .id(21L)
                .numero("MAS-002")
                .marque(TestFixtures.marque())
                .atelier(atelier)
                .build();
        when(userRepository.findByUsername("tech")).thenReturn(Optional.of(tech));
        when(masRepository.findByIdAndAtelierId(mas.getId(), atelier.getId())).thenReturn(Optional.of(mas));
        when(masRepository.findByIdAndAtelierId(21L, atelier.getId())).thenReturn(Optional.of(mas2));
        AtomicLong seq = new AtomicLong(10);
        when(interventionTechniqueRepository.save(any(InterventionTechnique.class))).thenAnswer(inv -> {
            InterventionTechnique e = inv.getArgument(0);
            e.setId(seq.getAndIncrement());
            return e;
        });

        List<InterventionTechniqueResponse> created =
                service.create(baseRequest(List.of(mas.getId(), 21L)), "tech");

        assertThat(created).hasSize(2);
        assertThat(created.get(0).getVisiteGroupeId()).isEqualTo(created.get(1).getVisiteGroupeId());
    }

    @Test
    void create_rejectsEmptyMasIds() {
        when(userRepository.findByUsername("tech")).thenReturn(Optional.of(tech));
        InterventionTechniqueRequest request = baseRequest(List.of());
        request.setMasIds(List.of());

        assertThatThrownBy(() -> service.create(request, "tech"))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getReason())
                .asString()
                .contains("MAS");
    }

    @Test
    void create_rejectsCommandeNotLinkedToMas() {
        when(userRepository.findByUsername("tech")).thenReturn(Optional.of(tech));
        Device otherDevice = TestFixtures.device();
        otherDevice.setMas(Mas.builder().id(999L).numero("OTHER").atelier(atelier).build());
        Commande commande = Commande.builder()
                .id(70L)
                .atelier(atelier)
                .technicien(tech)
                .technicienNom("tech")
                .message("cmd")
                .status("PENDING")
                .lignes(new ArrayList<>())
                .build();
        commande.addLigne(CommandeLigne.builder().device(otherDevice).quantite(1).build());
        when(commandeRepository.findByIdWithRelations(70L, atelier.getId())).thenReturn(Optional.of(commande));

        InterventionTechniqueRequest request = baseRequest(List.of(mas.getId()));
        request.setCommandeId(70L);

        assertThatThrownBy(() -> service.create(request, "tech"))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getReason())
                .asString()
                .containsIgnoringCase("commande");
    }

    @Test
    void create_acceptsLinkedCommandeAndBon() {
        when(userRepository.findByUsername("tech")).thenReturn(Optional.of(tech));
        when(masRepository.findByIdAndAtelierId(mas.getId(), atelier.getId())).thenReturn(Optional.of(mas));

        Device device = TestFixtures.device();
        Commande commande = Commande.builder()
                .id(71L)
                .atelier(atelier)
                .technicien(tech)
                .technicienNom("tech")
                .message("cmd")
                .status("RECEIVED")
                .lignes(new ArrayList<>())
                .build();
        commande.addLigne(CommandeLigne.builder().device(device).quantite(2).build());
        when(commandeRepository.findByIdWithRelations(71L, atelier.getId())).thenReturn(Optional.of(commande));

        Intervention bon = Intervention.builder()
                .id(9L)
                .numero("BI-100-2026-00001")
                .atelier(atelier)
                .mas(mas)
                .motif("Panne")
                .travaux("OK")
                .build();
        when(interventionRepository.findByIdWithRelations(9L, atelier.getId())).thenReturn(Optional.of(bon));
        when(interventionTechniqueRepository.save(any(InterventionTechnique.class))).thenAnswer(inv -> {
            InterventionTechnique e = inv.getArgument(0);
            e.setId(5L);
            return e;
        });

        InterventionTechniqueRequest request = baseRequest(List.of(mas.getId()));
        request.setCommandeId(71L);
        request.setBonInterventionId(9L);

        List<InterventionTechniqueResponse> created = service.create(request, "tech");
        assertThat(created).hasSize(1);
        assertThat(created.getFirst().getCommandeId()).isEqualTo(71L);
        assertThat(created.getFirst().getBonInterventionId()).isEqualTo(9L);
        assertThat(created.getFirst().getBonInterventionNumero()).isEqualTo("BI-100-2026-00001");
    }

    @Test
    void create_withAssocierFit_callsFitService() {
        when(userRepository.findByUsername("tech")).thenReturn(Optional.of(tech));
        when(masRepository.findByIdAndAtelierId(mas.getId(), atelier.getId())).thenReturn(Optional.of(mas));

        Fit fit = Fit.builder().id(3L).atelier(atelier).mas(mas).numeroMachineCasino(mas.getNumero()).build();
        FitLigne ligne = FitLigne.builder().id(4L).fit(fit).motifNatureOperations("x").build();
        when(fitService.appendFromTechnicalVisit(
                eq(atelier),
                eq(mas),
                any(),
                any(),
                eq(SIG_ADMIN),
                eq(SIG_TECH),
                eq("Admin Nom"),
                eq("Tech Nom"),
                isNull())).thenReturn(ligne);

        when(interventionTechniqueRepository.save(any(InterventionTechnique.class))).thenAnswer(inv -> {
            InterventionTechnique e = inv.getArgument(0);
            e.setId(8L);
            return e;
        });

        InterventionTechniqueRequest request = baseRequest(List.of(mas.getId()));
        request.setAssocierFit(true);
        request.setSignatureAdmin(SIG_ADMIN);
        request.setSignatureTechnicien(SIG_TECH);
        request.setSignataireAdminNom("Admin Nom");
        request.setSignataireTechnicienNom("Tech Nom");

        List<InterventionTechniqueResponse> created = service.create(request, "tech");
        assertThat(created).hasSize(1);
        assertThat(created.getFirst().getFitId()).isEqualTo(3L);
        assertThat(created.getFirst().getFitLigneId()).isEqualTo(4L);
        verify(fitService).appendFromTechnicalVisit(
                eq(atelier), eq(mas), any(), any(), eq(SIG_ADMIN), eq(SIG_TECH),
                eq("Admin Nom"), eq("Tech Nom"), isNull());
    }

    @Test
    void findById_returnsMappedResponse() {
        InterventionTechnique entity = InterventionTechnique.builder()
                .id(12L)
                .visiteGroupeId("vg")
                .atelier(atelier)
                .mas(mas)
                .dateIntervention(LocalDateTime.of(2026, 8, 15, 9, 0))
                .technicien(tech)
                .technicienNom("User Demo")
                .motif("Contrôle")
                .travaux("RAS")
                .build();
        when(interventionTechniqueRepository.findByIdAndAtelierId(12L, atelier.getId()))
                .thenReturn(Optional.of(entity));

        InterventionTechniqueResponse response = service.findById(12L);
        assertThat(response.getId()).isEqualTo(12L);
        assertThat(response.getMasNumero()).isEqualTo("MAS-001");
        assertThat(response.getMotif()).isEqualTo("Contrôle");
    }

    @Test
    void findByMasId_listsForMas() {
        when(masRepository.findByIdAndAtelierId(mas.getId(), atelier.getId())).thenReturn(Optional.of(mas));
        when(interventionTechniqueRepository.findByAtelierIdAndMasId(atelier.getId(), mas.getId()))
                .thenReturn(List.of());
        assertThat(service.findByMasId(mas.getId())).isEmpty();
    }

    @Test
    void findAll_delegatesToRepository() {
        when(interventionTechniqueRepository.findAllByAtelierId(atelier.getId())).thenReturn(List.of());
        assertThat(service.findAll()).isEmpty();
    }

    private static InterventionTechniqueRequest baseRequest(List<Long> masIds) {
        InterventionTechniqueRequest request = new InterventionTechniqueRequest();
        request.setDateIntervention(LocalDateTime.of(2026, 8, 15, 11, 0));
        request.setMasIds(masIds);
        request.setMotif("Panne lecteur");
        request.setTravaux("Remplacement");
        request.setDiagnostic("Erreur 12");
        request.setEmplacement("Zone A");
        request.setObservations("OK");
        request.setAssocierFit(false);
        return request;
    }
}
