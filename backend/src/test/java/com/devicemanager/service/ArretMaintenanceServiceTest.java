package com.devicemanager.service;

import com.devicemanager.dto.ArretMaintenanceRequest;
import com.devicemanager.entity.Atelier;
import com.devicemanager.entity.ArretMaintenance;
import com.devicemanager.entity.Mas;
import com.devicemanager.entity.User;
import com.devicemanager.repository.ArretMaintenanceRepository;
import com.devicemanager.repository.MasRepository;
import com.devicemanager.repository.UserRepository;
import com.devicemanager.support.TestFixtures;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ArretMaintenanceServiceTest {

    private static final String SIG =
            "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==";

    @Mock private ArretMaintenanceRepository arretMaintenanceRepository;
    @Mock private MasRepository masRepository;
    @Mock private UserRepository userRepository;
    @Mock private AtelierService atelierService;
    @InjectMocks private ArretMaintenanceService arretMaintenanceService;

    @Test
    void declareArret_requiresSignatureWhenRegistreAJour() {
        Atelier atelier = TestFixtures.atelier();
        Mas mas = TestFixtures.mas();
        User admin = TestFixtures.user("admin", "ADMIN");
        when(atelierService.requireCurrentAtelier()).thenReturn(atelier);
        when(masRepository.findByIdAndAtelierId(mas.getId(), atelier.getId())).thenReturn(Optional.of(mas));
        when(arretMaintenanceRepository.existsByMasIdAndDateHeureRepriseIsNull(mas.getId())).thenReturn(false);
        when(userRepository.findByUsername(admin.getUsername())).thenReturn(Optional.of(admin));

        ArretMaintenanceRequest request = new ArretMaintenanceRequest();
        request.setMasId(mas.getId());
        request.setMotifArret("Maintenance planifiée");
        request.setRegistreTechniqueAJour(true);

        assertThatThrownBy(() -> arretMaintenanceService.declareArret(request, admin.getUsername()))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getReason())
                .asString()
                .contains("arrêt");
    }

    @Test
    void declareArret_persistsActiveStop() {
        Atelier atelier = TestFixtures.atelier();
        Mas mas = TestFixtures.mas();
        User admin = TestFixtures.user("admin", "ADMIN");
        when(atelierService.requireCurrentAtelier()).thenReturn(atelier);
        when(masRepository.findByIdAndAtelierId(mas.getId(), atelier.getId())).thenReturn(Optional.of(mas));
        when(arretMaintenanceRepository.existsByMasIdAndDateHeureRepriseIsNull(mas.getId())).thenReturn(false);
        when(userRepository.findByUsername(admin.getUsername())).thenReturn(Optional.of(admin));
        when(arretMaintenanceRepository.save(any(ArretMaintenance.class))).thenAnswer(inv -> {
            ArretMaintenance a = inv.getArgument(0);
            a.setId(1L);
            return a;
        });

        ArretMaintenanceRequest request = new ArretMaintenanceRequest();
        request.setMasId(mas.getId());
        request.setMotifArret("Panne");
        request.setRegistreTechniqueAJour(true);
        request.setSignatureArret(SIG);

        var response = arretMaintenanceService.declareArret(request, admin.getUsername());
        assertThat(response.getMasNumero()).isEqualTo(mas.getNumero());
        assertThat(response.isActif()).isTrue();
        assertThat(response.isRegistreTechniqueAJour()).isTrue();
    }
}
