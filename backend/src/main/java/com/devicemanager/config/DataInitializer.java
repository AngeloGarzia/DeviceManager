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
    private final DenoRepository denoRepository;
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

        ensureDeno(new java.math.BigDecimal("0.01"), "0,01 €");
        ensureDeno(new java.math.BigDecimal("0.02"), "0,02 €");
        ensureDeno(new java.math.BigDecimal("0.05"), "0,05 €");
        ensureDeno(new java.math.BigDecimal("0.10"), "0,10 €");
        ensureDeno(new java.math.BigDecimal("0.20"), "0,20 €");
        ensureDeno(new java.math.BigDecimal("0.50"), "0,50 €");
        ensureDeno(new java.math.BigDecimal("1.00"), "1,00 €");
        ensureDeno(new java.math.BigDecimal("2.00"), "2,00 €");

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
            ensureDemoMasBalaruc();
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

    private void ensureDeno(java.math.BigDecimal valeur, String label) {
        denoRepository.findByValeur(valeur)
                .orElseGet(() -> denoRepository.save(Deno.builder().valeur(valeur).label(label).build()));
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

    private void ensureDemoMasBalaruc() {
        Atelier atelier = resolveBalarucAtelier();
        if (atelier == null) {
            return;
        }
        Long atelierId = atelier.getId();
        ensureMasIfAbsent(atelier, atelierId, demoMas(
                "BAL-001", "ARISTOCRAT", "S-A01", "94.50", "0.01",
                MasStatut.UTILISEE, "2019-03-15", "Machine à sous", "AR-SN-1001", null, null));
        ensureMasIfAbsent(atelier, atelierId, demoMas(
                "BAL-002", "IGT", "S-A02", "92.00", "0.02",
                MasStatut.UTILISEE, "2020-06-01", "Machine à sous", "IGT-SN-2202", null, null));
        ensureMasIfAbsent(atelier, atelierId, demoMas(
                "BAL-003", "NOVOMATIC", "S-B12", "95.25", "0.05",
                MasStatut.UTILISEE, "2021-01-20", "Roulette électronique", "NOV-SN-3312", null, null));
        ensureMasIfAbsent(atelier, atelierId, demoMas(
                "BAL-004", "KONAMI", "S-B13", "93.80", "0.10",
                MasStatut.UTILISEE, "2018-11-08", "Machine à sous", "KON-SN-4404", null, null));
        ensureMasIfAbsent(atelier, atelierId, demoMas(
                "BAL-005", "SCIENTIFIC_GAMES", "S-C01", "96.10", "0.20",
                MasStatut.UTILISEE, "2022-04-12", "Poker vidéo", "SG-SN-5505", null, null));
        ensureMasIfAbsent(atelier, atelierId, demoMas(
                "BAL-006", "BALLY", "RES-01", "91.50", "0.50",
                MasStatut.EN_RESERVE, "2017-09-03", "Machine à sous", "BAL-SN-6606", "2025-12-01", null));
        ensureMasIfAbsent(atelier, atelierId, demoMas(
                "BAL-007", "WMS", "RES-02", "90.00", "1.00",
                MasStatut.EN_RESERVE, "2016-05-22", "Machine à sous", "WMS-SN-7707", "2026-01-15", null));
        ensureMasIfAbsent(atelier, atelierId, demoMas(
                "BAL-008", "MERKUR", null, "94.00", "0.05",
                MasStatut.VENDUE, "2015-02-10", "Machine à sous", "MER-SN-8808", "2024-08-30",
                "Reprise fournisseur Merkur — lot 2024"));
        ensureMasIfAbsent(atelier, atelierId, demoMas(
                "BAL-009", "EVERI", null, "93.20", "0.20",
                MasStatut.VENDUE, "2014-07-18", "Machine à sous", "EVR-SN-9909", "2025-03-05",
                "Casino partenaire — transfert intergroupe"));
        ensureMasIfAbsent(atelier, atelierId, demoMas(
                "BAL-010", "AMATIC", "OLD-03", "89.90", "2.00",
                MasStatut.DETRUITE, "2012-10-01", "Machine à sous", "AMA-SN-1010", "2026-02-20", null));
    }

    private Atelier resolveBalarucAtelier() {
        List<Atelier> balarucAteliers = atelierRepository.findAll().stream()
                .filter(a -> a.getCasino() != null
                        && a.getCasino().getNom() != null
                        && a.getCasino().getNom().toLowerCase().contains("balaruc"))
                .sorted((a, b) -> {
                    boolean aExact = "Casino Balaruc-les-Bains".equalsIgnoreCase(a.getNom());
                    boolean bExact = "Casino Balaruc-les-Bains".equalsIgnoreCase(b.getNom());
                    if (aExact != bExact) {
                        return aExact ? -1 : 1;
                    }
                    return a.getNom().compareToIgnoreCase(b.getNom());
                })
                .toList();
        return balarucAteliers.isEmpty() ? null : balarucAteliers.getFirst();
    }

    private void ensureMasIfAbsent(Atelier atelier, Long atelierId, Mas draft) {
        if (masRepository.existsByNumeroIgnoreCaseAndAtelierId(draft.getNumero(), atelierId)) {
            return;
        }
        draft.setAtelier(atelier);
        masRepository.save(draft);
    }

    private Mas demoMas(
            String numero,
            String marqueCode,
            String socle,
            String taux,
            String denoValeur,
            MasStatut statut,
            String dateMiseEnService,
            String typeMachine,
            String numeroSerie,
            String dateCessation,
            String destination) {
        Mas mas = Mas.builder()
                .numero(numero)
                .numeroSocle(socle)
                .tauxRedistribution(new java.math.BigDecimal(taux))
                .marque(requireMarque(marqueCode))
                .deno(requireDeno(new java.math.BigDecimal(denoValeur)))
                .typeMachine(typeMachine)
                .numeroSerie(numeroSerie)
                .dateMiseEnService(java.time.LocalDate.parse(dateMiseEnService))
                .build();
        mas.applyStatut(statut);
        if (dateCessation != null) {
            mas.setDateCessation(java.time.LocalDate.parse(dateCessation));
        }
        if (destination != null) {
            mas.setDestinationMachineUsagee(destination);
        }
        return mas;
    }

    private Deno requireDeno(java.math.BigDecimal valeur) {
        return denoRepository.findByValeur(valeur)
                .orElseThrow(() -> new IllegalStateException("Deno absente: " + valeur));
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
