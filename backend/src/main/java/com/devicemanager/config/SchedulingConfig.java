package com.devicemanager.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Active le planificateur Spring ({@code @Scheduled}) pour les jobs techniques
 * (keep-alive, flush logs) et les rappels métier.
 * <p>
 * Indépendant de {@code app.keepalive.enabled} : le keep-alive reste optionnel
 * via {@link RenderKeepAliveScheduler}.
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
