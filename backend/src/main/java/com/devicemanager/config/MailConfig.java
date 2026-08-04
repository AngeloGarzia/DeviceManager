package com.devicemanager.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

/**
 * Configuration du courrier sortant.
 * <p>
 * Fournit un bean {@link JavaMailSender} minimal si aucun n'est déjà configuré,
 * afin que l'application démarre même sans SMTP ({@code app.mail.enabled=false}, mode simulation).
 */
@Configuration
public class MailConfig {

    /**
     * Bean {@link JavaMailSender} par défaut (non configuré) lorsque SMTP est absent.
     *
     * @return implémentation vide utilisable en mode simulation
     */
    @Bean
    @ConditionalOnMissingBean(JavaMailSender.class)
    public JavaMailSender javaMailSender() {
        return new JavaMailSenderImpl();
    }
}
