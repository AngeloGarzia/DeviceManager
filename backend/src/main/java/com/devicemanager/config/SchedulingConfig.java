package com.devicemanager.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Active le planificateur Spring ({@code @Scheduled}) lorsque le keep-alive Render est activé.
 * <p>
 * Conditionné par {@code app.keepalive.enabled=true}, en tandem avec
 * {@link RenderKeepAliveScheduler}.
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "app.keepalive.enabled", havingValue = "true")
public class SchedulingConfig {
}
