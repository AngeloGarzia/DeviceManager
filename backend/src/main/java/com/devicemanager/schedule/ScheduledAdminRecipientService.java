package com.devicemanager.schedule;

import com.devicemanager.entity.Atelier;
import com.devicemanager.entity.Casino;
import com.devicemanager.entity.User;
import com.devicemanager.repository.UserRepository;
import com.devicemanager.security.Roles;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Résout les destinataires ADMIN / SUPER_ADMIN concernés par un casino
 * (utilisateurs du même groupe, filtrés par atelier préféré si renseigné).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ScheduledAdminRecipientService {

    private final UserRepository userRepository;

    /**
     * E-mails des admins du groupe du casino, concernés par ce casino.
     * <ul>
     *   <li>sans atelier préféré → tous les casinos du groupe</li>
     *   <li>avec atelier préféré → uniquement si l'atelier appartient à ce casino</li>
     * </ul>
     */
    @Transactional(readOnly = true)
    public List<String> emailsForCasino(Casino casino) {
        if (casino == null || casino.getId() == null || casino.getGroupe() == null
                || casino.getGroupe().getId() == null) {
            return List.of();
        }
        Long groupeId = casino.getGroupe().getId();
        Long casinoId = casino.getId();
        List<User> admins = userRepository.findAdminLikeByGroupeId(groupeId);
        Set<String> emails = new LinkedHashSet<>();
        for (User user : admins) {
            if (!user.isReceiveAlertMails()) {
                continue;
            }
            if (!isConcernedByCasino(user, casinoId)) {
                continue;
            }
            String email = normalizeEmail(user.getEmail());
            if (email != null) {
                emails.add(email);
            }
        }
        return new ArrayList<>(emails);
    }

    /** Raccourci depuis un atelier (charge casino + groupe). */
    @Transactional(readOnly = true)
    public List<String> emailsForAtelier(Atelier atelier) {
        if (atelier == null || atelier.getCasino() == null) {
            return List.of();
        }
        return emailsForCasino(atelier.getCasino());
    }

    static boolean isConcernedByCasino(User user, Long casinoId) {
        if (user == null || !Roles.isAdminLike(user.getRole())) {
            return false;
        }
        Atelier preferred = user.getPreferredAtelier();
        if (preferred == null) {
            return true;
        }
        if (preferred.getCasino() == null || preferred.getCasino().getId() == null) {
            return true;
        }
        return casinoId.equals(preferred.getCasino().getId());
    }

    static String normalizeEmail(String email) {
        if (email == null || email.isBlank() || !email.contains("@")) {
            return null;
        }
        return email.trim().toLowerCase();
    }
}
