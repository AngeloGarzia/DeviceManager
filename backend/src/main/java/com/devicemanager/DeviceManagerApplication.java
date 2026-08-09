package com.devicemanager;

import com.devicemanager.config.DotEnvLoader;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Point d'entrée de l'application Spring Boot DeviceManager.
 * <p>
 * Charge les variables d'environnement depuis un fichier {@code .env} avant le démarrage
 * du contexte Spring, afin que les secrets (JWT, clés IA, SMTP, etc.) soient disponibles
 * dès l'initialisation.
 */
@SpringBootApplication
public class DeviceManagerApplication {

    /**
     * Démarre l'application après chargement du fichier dotenv adapté au profil actif.
     * {@link DotEnvLoader} active aussi {@code spring.profiles.active}
     * ({@code production} / {@code development}) selon {@code APP_ENV} si non déjà défini.
     *
     * @param args arguments de ligne de commande passés à Spring Boot
     */
    public static void main(String[] args) {
        DotEnvLoader.load();
        SpringApplication.run(DeviceManagerApplication.class, args);
    }
}
