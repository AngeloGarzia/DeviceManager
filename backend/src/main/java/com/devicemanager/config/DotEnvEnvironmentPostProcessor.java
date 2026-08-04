package com.devicemanager.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.util.HashMap;
import java.util.Map;

/**
 * Post-processeur Spring Boot exécuté très tôt au démarrage pour injecter les variables
 * dotenv dans l'{@link org.springframework.core.env.Environment} Spring.
 * <p>
 * Délègue le chargement du fichier à {@link DotEnvLoader} et ajoute une source de propriétés
 * nommée {@code dotenv} en première position, afin que {@code @Value} et {@code application.yml}
 * puissent résoudre les secrets avant l'initialisation des beans.
 */
@Order(Ordered.HIGHEST_PRECEDENCE)
public class DotEnvEnvironmentPostProcessor implements EnvironmentPostProcessor {

    /**
     * Charge le dotenv et l'enregistre comme source de propriétés Spring prioritaire.
     *
     * @param environment environnement Spring configurable
     * @param application application Spring en cours de démarrage
     */
    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        Map<String, String> loaded = DotEnvLoader.load();
        if (loaded.isEmpty()) {
            return;
        }
        Map<String, Object> asObjects = new HashMap<>(loaded);
        environment.getPropertySources().addFirst(new MapPropertySource("dotenv", asObjects));
    }
}
