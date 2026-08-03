package com.devicemanager.config;

import com.devicemanager.entity.*;
import com.devicemanager.repository.*;
import com.devicemanager.security.Roles;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

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

        upsertUser("admin", "admin123", Roles.ADMIN, circus);
        upsertUser("tech", "tech123", Roles.TECHNICIEN, circus);

        userRepository.findByUsername("tech").ifPresent(user -> {
            if ("TECH".equals(user.getRole())) {
                user.setRole(Roles.TECHNICIEN);
                userRepository.save(user);
            }
        });

        if (defaultAtelier != null) {
            Long countSfm = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM sfm", Integer.class) == null
                    ? 0L
                    : jdbcTemplate.queryForObject("SELECT COUNT(*) FROM sfm", Integer.class).longValue();
            if (countSfm == 0) {
                seedSfm(defaultAtelier);
            }
            Long countMas = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM mas", Integer.class) == null
                    ? 0L
                    : jdbcTemplate.queryForObject("SELECT COUNT(*) FROM mas", Integer.class).longValue();
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
                .build());
        salle.addContact(SfmContact.builder()
                .nom("Paul Bernard")
                .telephone("0611223344")
                .email("paul.bernard@casino.local")
                .build());
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
                .build());
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

    private void upsertUser(String username, String rawPassword, String role, Groupe groupe) {
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
            if (changed) {
                userRepository.save(user);
            }
        }, () -> userRepository.save(User.builder()
                .username(username)
                .nom(username.equals("admin") ? "Admin" : "Technicien")
                .prenom(username.equals("admin") ? "Système" : "Demo")
                .password(passwordEncoder.encode(rawPassword))
                .role(role)
                .groupe(groupe)
                .build()));
    }
}
