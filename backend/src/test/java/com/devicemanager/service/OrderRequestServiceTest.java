package com.devicemanager.service;

import com.devicemanager.dto.OrderRequestDto;
import com.devicemanager.dto.OrderRequestResponse;
import com.devicemanager.entity.Commande;
import com.devicemanager.repository.CommandeRepository;
import com.devicemanager.repository.DeviceRepository;
import com.devicemanager.repository.UserRepository;
import com.devicemanager.security.Roles;
import com.devicemanager.support.TestFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderRequestServiceTest {

    private static final Logger log = LoggerFactory.getLogger(OrderRequestServiceTest.class);

    @Mock private CommandeRepository commandeRepository;
    @Mock private DeviceRepository deviceRepository;
    @Mock private UserRepository userRepository;
    @Mock private MailService mailService;
    @Mock private AtelierService atelierService;
    @InjectMocks private OrderRequestService orderRequestService;

    @BeforeEach
    void setUp() {
        lenient().when(atelierService.requireCurrentAtelier()).thenReturn(TestFixtures.atelier());
    }

    @Test
    void create_mergesQuantitiesAndSendsMail() {
        log.info("Test create order request");
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
        assertThat(response.getTechnicienNom()).isEqualTo("tech");
        verify(mailService).sendOrderRequestToAdmin(contains("1 pièce"), contains("Urgent"));
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
                .isEqualTo("Pièce détachée introuvable: 999");
    }

    @Test
    void findAll_listsForAtelier() {
        when(commandeRepository.findAllWithRelationsOrderByDateDesc(100L)).thenReturn(List.of());

        assertThat(orderRequestService.findAll()).isEmpty();
    }
}
