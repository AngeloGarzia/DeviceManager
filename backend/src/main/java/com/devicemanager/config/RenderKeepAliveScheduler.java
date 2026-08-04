package com.devicemanager.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Planificateur de keep-alive pour l'hébergement Render (plan gratuit).
 * <p>
 * Envoie un GET HTTP périodique vers l'URL publique de l'API (par défaut
 * {@code RENDER_EXTERNAL_URL/actuator/health}) afin de retarder la mise en veille
 * (~15 minutes sans trafic entrant sur Render free).
 * <p>
 * <strong>Limites :</strong> ne réveille pas un service déjà endormi ; le ping doit
 * transiter par l'URL publique (pas {@code localhost}) pour compter comme trafic entrant.
 * Activé via {@code app.keepalive.enabled=true}.
 */
@Component
@ConditionalOnProperty(name = "app.keepalive.enabled", havingValue = "true")
@Slf4j
public class RenderKeepAliveScheduler {

    private final RestClient restClient;
    private final String pingUrl;

    /**
     * Résout l'URL de ping ({@code app.keepalive.url} ou dérivée de {@code RENDER_EXTERNAL_URL})
     * et configure un client HTTP avec timeouts courts.
     *
     * @param configuredUrl       URL explicite ({@code APP_KEEPALIVE_URL}), optionnelle
     * @param renderExternalUrl   URL publique Render injectée par la plateforme, optionnelle
     */
    public RenderKeepAliveScheduler(
            @Value("${app.keepalive.url:}") String configuredUrl,
            @Value("${RENDER_EXTERNAL_URL:}") String renderExternalUrl) {
        this.pingUrl = resolvePingUrl(configuredUrl, renderExternalUrl);
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(10_000);
        factory.setReadTimeout(15_000);
        this.restClient = RestClient.builder().requestFactory(factory).build();
        if (this.pingUrl == null) {
            log.warn("Keep-alive activé mais aucune URL (APP_KEEPALIVE_URL ou RENDER_EXTERNAL_URL)");
        } else {
            log.info("Keep-alive Render activé → ping {}", this.pingUrl);
        }
    }

    /**
     * Ping HTTP planifié (intervalle par défaut : 14 minutes, délai initial : 1 minute).
     * Les échecs sont journalisés sans interrompre l'application.
     */
    @Scheduled(
            fixedDelayString = "${app.keepalive.interval-ms:840000}",
            initialDelayString = "${app.keepalive.initial-delay-ms:60000}")
    public void ping() {
        if (pingUrl == null || pingUrl.isBlank()) {
            return;
        }
        try {
            var response = restClient.get().uri(pingUrl).retrieve().toBodilessEntity();
            log.debug("Keep-alive OK status={}", response.getStatusCode().value());
        } catch (Exception ex) {
            log.warn("Keep-alive échoué ({}): {}", pingUrl, ex.getMessage());
        }
    }

    private static String resolvePingUrl(String configuredUrl, String renderExternalUrl) {
        if (configuredUrl != null && !configuredUrl.isBlank()) {
            return configuredUrl.trim();
        }
        if (renderExternalUrl != null && !renderExternalUrl.isBlank()) {
            String base = renderExternalUrl.trim().replaceAll("/$", "");
            return base + "/actuator/health";
        }
        return null;
    }
}
