package com.devicemanager.schedule;

import com.devicemanager.entity.Atelier;
import com.devicemanager.entity.Casino;
import com.devicemanager.entity.Groupe;
import com.devicemanager.entity.User;
import com.devicemanager.repository.UserRepository;
import com.devicemanager.security.Roles;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ScheduledAdminRecipientServiceTest {

    @Mock private UserRepository userRepository;
    @InjectMocks private ScheduledAdminRecipientService service;

    @Test
    void emailsForCasino_filtersByPreferredCasino() {
        Groupe groupe = Groupe.builder().id(1L).nom("G").build();
        Casino casinoA = Casino.builder().id(10L).nom("A").groupe(groupe).build();
        Casino casinoB = Casino.builder().id(20L).nom("B").groupe(groupe).build();

        User adminAll = User.builder()
                .id(1L).role(Roles.ADMIN).email("all@test.local").groupe(groupe).build();
        User adminA = User.builder()
                .id(2L).role(Roles.SUPER_ADMIN).email("a@test.local").groupe(groupe)
                .preferredAtelier(Atelier.builder().id(100L).casino(casinoA).build())
                .build();
        User adminB = User.builder()
                .id(3L).role(Roles.ADMIN).email("b@test.local").groupe(groupe)
                .preferredAtelier(Atelier.builder().id(200L).casino(casinoB).build())
                .build();

        when(userRepository.findAdminLikeByGroupeId(1L)).thenReturn(List.of(adminAll, adminA, adminB));

        assertThat(service.emailsForCasino(casinoA))
                .containsExactlyInAnyOrder("all@test.local", "a@test.local");
        assertThat(service.emailsForCasino(casinoB))
                .containsExactlyInAnyOrder("all@test.local", "b@test.local");
    }

    @Test
    void emailsForCasino_skipsOptedOutAdmins() {
        Groupe groupe = Groupe.builder().id(1L).nom("G").build();
        Casino casino = Casino.builder().id(10L).nom("A").groupe(groupe).build();
        when(userRepository.findAdminLikeByGroupeId(1L)).thenReturn(List.of(
                User.builder().id(1L).role(Roles.ADMIN).email("yes@test.local")
                        .groupe(groupe).receiveAlertMails(true).build(),
                User.builder().id(2L).role(Roles.SUPER_ADMIN).email("no@test.local")
                        .groupe(groupe).receiveAlertMails(false).build()));

        assertThat(service.emailsForCasino(casino)).containsExactly("yes@test.local");
    }

    @Test
    void emailsForCasino_skipsBlankEmails() {
        Groupe groupe = Groupe.builder().id(1L).nom("G").build();
        Casino casino = Casino.builder().id(10L).nom("A").groupe(groupe).build();
        when(userRepository.findAdminLikeByGroupeId(1L)).thenReturn(List.of(
                User.builder().id(1L).role(Roles.ADMIN).email(" ").groupe(groupe).build(),
                User.builder().id(2L).role(Roles.ADMIN).email("ok@test.local").groupe(groupe).build()));

        assertThat(service.emailsForCasino(casino)).containsExactly("ok@test.local");
    }
}
