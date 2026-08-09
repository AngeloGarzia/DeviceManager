package com.devicemanager.config;

import com.devicemanager.entity.*;
import com.devicemanager.repository.*;
import com.devicemanager.security.Roles;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Initialisation des données de référence / démonstration au démarrage.
 * <p>
 * Crée le groupe Circus, des casinos et ateliers, le catalogue de marques MAS, rattache les
 * données orphelines à l'atelier par défaut, et seed les SFM/MAS si les tables sont vides.
 * Les comptes demo {@code admin} / {@code tech} ne sont provisionnés que hors profil {@code production}.
 */
@Component
@Order(2)
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final SfmRepository sfmRepository;
    private final MasRepository masRepository;
    private final MarqueMasRepository marqueMasRepository;
    private final GroupeRepository groupeRepository;
    private final CasinoRepository casinoRepository;
    private final AtelierRepository atelierRepository;
    private final PasswordEncoder passwordEncoder;
    private final JdbcTemplate jdbcTemplate;
    private final Environment environment;

    /**
     * Exécute le seed idempotent : entités de référence, utilisateurs demo et données SFM/MAS.
     *
     * @param args arguments de ligne de commande (non utilisés)
     */
    @Override
    @Transactional
    public void run(String... args) {
        Groupe circus = ensureGroupe("Circus");
        List<String> casinoNoms = List.of(
                "Balaruc-les-Bains",
                "Barbotan",
                "Carnac",
                "Briançon",
                "Allevard",
                "Leucate"
        );
        Atelier defaultAtelier = null;
        for (String casinoNom : casinoNoms) {
            Casino casino = ensureCasino(circus, casinoNom);
            Atelier atelier = ensureAtelier(casino, "Atelier Casino " + casinoNom);
            if (defaultAtelier == null) {
                defaultAtelier = atelier;
            }
        }

        ensureMarque("ARISTOCRAT", "Aristocrat");
        ensureMarque("IGT", "IGT");
        ensureMarque("NOVOMATIC", "Novomatic");
        ensureMarque("SCIENTIFIC_GAMES", "Scientific Games");
        ensureMarque("KONAMI", "Konami");
        ensureMarque("AINSWORTH", "Ainsworth");
        ensureMarque("BALLY", "Bally");
        ensureMarque("WMS", "WMS");
        ensureMarque("EVERI", "Everi");
        ensureMarque("AMATIC", "Amatic");
        ensureMarque("MERKUR", "Merkur");
        ensureMarque("AUTRES", "Autres");

        assignExistingDataToAtelier(defaultAtelier);

        if (!environment.matchesProfiles("production")) {
            upsertUser("admin", "admin123", Roles.ADMIN, circus, null);
            upsertUser("tech", "tech123", Roles.TECHNICIEN, circus, defaultAtelier);

            userRepository.findByUsername("tech").ifPresent(user -> {
                if ("TECH".equals(user.getRole())) {
                    user.setRole(Roles.TECHNICIEN);
                    userRepository.save(user);
                }
            });
        }

        if (defaultAtelier != null) {
            int countSfm = Optional.ofNullable(
                    jdbcTemplate.queryForObject("SELECT COUNT(*) FROM sfm", Integer.class)).orElse(0);
            if (countSfm == 0) {
                seedSfm(defaultAtelier);
            }
            int countMas = Optional.ofNullable(
                    jdbcTemplate.queryForObject("SELECT COUNT(*) FROM mas", Integer.class)).orElse(0);
            if (countMas == 0) {
                seedMas(defaultAtelier);
            }
        }
    }

    private void assignExistingDataToAtelier(Atelier atelier) {
        if (atelier == null) {
            return;
        }
        Long id = atelier.getId();
        tryUpdate("UPDATE device SET atelier_id = ? WHERE atelier_id IS NULL", id);
        tryUpdate("UPDATE mas SET atelier_id = ? WHERE atelier_id IS NULL", id);
        tryUpdate("UPDATE sfm SET atelier_id = ? WHERE atelier_id IS NULL", id);
        tryUpdate("UPDATE commande SET atelier_id = ? WHERE atelier_id IS NULL", id);
        tryUpdate("UPDATE users SET groupe_id = ? WHERE groupe_id IS NULL", atelier.getCasino().getGroupe().getId());
    }

    private void tryUpdate(String sql, Object arg) {
        try {
            jdbcTemplate.update(sql, arg);
        } catch (Exception ignored) {
            // colonnes absentes pendant le premier boot
        }
    }

    private Groupe ensureGroupe(String nom) {
        return groupeRepository.findByNomIgnoreCase(nom)
                .orElseGet(() -> groupeRepository.save(Groupe.builder().nom(nom).build()));
    }

    private Casino ensureCasino(Groupe groupe, String nom) {
        return casinoRepository.findByNomIgnoreCaseAndGroupeId(nom, groupe.getId())
                .orElseGet(() -> casinoRepository.save(Casino.builder().nom(nom).groupe(groupe).build()));
    }

    private Atelier ensureAtelier(Casino casino, String nom) {
        return atelierRepository.findByNomIgnoreCaseAndCasinoId(nom, casino.getId())
                .orElseGet(() -> atelierRepository.save(Atelier.builder().nom(nom).casino(casino).build()));
    }

    private void ensureMarque(String code, String label) {
        marqueMasRepository.findByCodeIgnoreCase(code)
                .orElseGet(() -> marqueMasRepository.save(MarqueMas.builder().code(code).label(label).build()));
    }

    private void seedSfm(Atelier atelier) {
        Sfm salle = Sfm.builder()
                .nom("SFM Salle Principale")
                .responsable("Marie Dupont")
                .telephone("0612345678")
                .email("marie.dupont@casino.local")
                .atelier(atelier)
                .build();
        salle.addContact(SfmContact.builder()
                .nom("Marie Dupont")
                .telephone("0612345678")
                .email("marie.dupont@casino.local")
                .technicienSfm(false)
                .build());
        SfmContact techPaul = SfmContact.builder()
                .nom("Paul Bernard")
                .telephone("0611223344")
                .email("paul.bernard@casino.local")
                .technicienSfm(true)
                .build();
        salle.addContact(techPaul);
        sfmRepository.save(salle);

        Sfm vip = Sfm.builder()
                .nom("SFM VIP")
                .responsable("Jean Martin")
                .telephone("0698765432")
                .email("jean.martin@casino.local")
                .atelier(atelier)
                .build();
        vip.addContact(SfmContact.builder()
                .nom("Jean Martin")
                .telephone("0698765432")
                .email("jean.martin@casino.local")
                .technicienSfm(false)
                .build());
        // Même technicien SFM rattaché à deux SFM
        vip.addContact(techPaul);
        sfmRepository.save(vip);
    }

    private void seedMas(Atelier atelier) {
        masRepository.save(Mas.builder()
                .numero("MAS-101")
                .marque(requireMarque("ARISTOCRAT"))
                .utilise(true)
                .atelier(atelier)
                .build());
        masRepository.save(Mas.builder()
                .numero("MAS-205")
                .marque(requireMarque("IGT"))
                .utilise(true)
                .atelier(atelier)
                .build());
        masRepository.save(Mas.builder()
                .numero("MAS-312")
                .marque(requireMarque("NOVOMATIC"))
                .utilise(false)
                .atelier(atelier)
                .build());
    }

    private MarqueMas requireMarque(String code) {
        return marqueMasRepository.findByCodeIgnoreCase(code)
                .orElseThrow(() -> new IllegalStateException("Marque absente: " + code));
    }

    private void upsertUser(String username, String rawPassword, String role, Groupe groupe, Atelier preferredAtelier) {
        boolean isAdmin = "admin".equals(username);
        String email = isAdmin ? "admin@devicemanager.local" : "tech@devicemanager.local";
        String nom = isAdmin ? "Admin" : "Technicien";
        String prenom = isAdmin ? "Système" : "Demo";
        userRepository.findByUsername(username).ifPresentOrElse(user -> {
            boolean changed = false;
            if (!role.equals(user.getRole())) {
                user.setRole(role);
                changed = true;
            }
            if (user.getGroupe() == null) {
                user.setGroupe(groupe);
                changed = true;
            }
            if (user.getEmail() == null || user.getEmail().isBlank() || user.getEmail().endsWith("@users.local")) {
                user.setEmail(email);
                changed = true;
            }
            if (user.getNom() == null || user.getNom().isBlank()) {
                user.setNom(nom);
                changed = true;
            }
            if (user.getPrenom() == null || user.getPrenom().isBlank()) {
                user.setPrenom(prenom);
                changed = true;
            }
            if (user.getPreferredAtelier() == null && preferredAtelier != null) {
                user.setPreferredAtelier(preferredAtelier);
                changed = true;
            }
            if (changed) {
                userRepository.save(user);
            }
        }, () -> userRepository.save(User.builder()
                .username(username)
                .nom(nom)
                .prenom(prenom)
                .email(email)
                .password(passwordEncoder.encode(rawPassword))
                .role(role)
                .groupe(groupe)
                .preferredAtelier(preferredAtelier)
                .mustChangePassword(true)
                .build()));
    }
}
