package com.devicemanager.support;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Base abstraite pour les tests d'intégration MySQL via Testcontainers.
 * <p>
 * Les classes {@code *IT} qui étendent cette base démarrent un conteneur MySQL
 * et injectent datasource + secret JWT via {@link DynamicPropertySource}.
 * Exclues du surefire par défaut ({@code *IT.java}) ; exécutées via failsafe
 * lorsque {@code -DskipITs=false}.
 */
@Testcontainers
public abstract class MySqlTestcontainer {

    private static final String JWT_SECRET = "integration-test-jwt-secret-key-32b-min!";

    @Container
    @SuppressWarnings("resource")
    protected static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("device_manager")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void registerDatasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("app.jwt.secret", () -> JWT_SECRET);
        registry.add("app.s3.enabled", () -> "false");
        registry.add("app.mail.enabled", () -> "false");
        registry.add("app.ai.enabled", () -> "false");
    }
}
