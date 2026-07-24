package com.devicemanager.support;

import com.devicemanager.entity.*;

public final class TestFixtures {

    private TestFixtures() {
    }

    public static Groupe groupe() {
        return Groupe.builder().id(1L).nom("Circus").build();
    }

    public static Casino casino() {
        return Casino.builder().id(10L).nom("Balaruc").groupe(groupe()).build();
    }

    public static Atelier atelier() {
        return Atelier.builder().id(100L).nom("Atelier Balaruc").casino(casino()).build();
    }

    public static MarqueMas marque() {
        return MarqueMas.builder().id(5L).code("NOVOMATIC").label("Novomatic").build();
    }

    public static Mas mas() {
        return Mas.builder()
                .id(20L)
                .numero("MAS-001")
                .marque(marque())
                .utilise(true)
                .atelier(atelier())
                .build();
    }

    public static Sfm sfm() {
        Sfm sfm = Sfm.builder()
                .id(30L)
                .nom("SFM Nord")
                .responsable("Jean")
                .telephone("0600000000")
                .email("jean@example.com")
                .atelier(atelier())
                .build();
        sfm.setMarques(new java.util.HashSet<>(java.util.List.of(marque())));
        return sfm;
    }

    public static Device device() {
        return Device.builder()
                .id(40L)
                .nom("Carte mère")
                .reference("REF-001")
                .usage("Remplacement")
                .dateAcquisition(java.time.LocalDate.of(2024, 1, 1))
                .obsolete(false)
                .photoKey("key")
                .photoUrl("/uploads/key")
                .contentType("image/jpeg")
                .fileSize(10L)
                .sfm(sfm())
                .mas(mas())
                .marque(marque())
                .atelier(atelier())
                .build();
    }

    public static User user(String username, String role) {
        return User.builder()
                .id(50L)
                .username(username)
                .password("encoded")
                .role(role)
                .groupe(groupe())
                .build();
    }
}
