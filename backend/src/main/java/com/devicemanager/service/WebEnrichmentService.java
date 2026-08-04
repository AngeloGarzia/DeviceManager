package com.devicemanager.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Enrichissement léger via APIs publiques (Wikipedia / DuckDuckGo) — sans clé API.
 * <p>
 * Complète le scan IA d'étiquettes de pièces détachées avec du contexte web
 * pour rédiger le champ « usage » de la fiche pièce.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WebEnrichmentService {

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(8))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    /**
     * Agrège des extraits web à partir de termes (marque, nom, référence).
     *
     * @param terms termes de recherche (valeurs nulles ou vides ignorées)
     * @return texte concaténé de snippets, ou chaîne vide si aucun résultat
     */
    public String gatherContext(String... terms) {
        String query = buildQuery(terms);
        if (query.isBlank()) {
            return "";
        }
        Set<String> snippets = new LinkedHashSet<>();
        try {
            snippets.addAll(searchWikipedia(query));
        } catch (Exception ex) {
            log.debug("Wikipedia enrichissement échoué: {}", ex.getMessage());
        }
        try {
            snippets.addAll(searchDuckDuckGo(query));
        } catch (Exception ex) {
            log.debug("DuckDuckGo enrichissement échoué: {}", ex.getMessage());
        }
        if (snippets.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        int i = 0;
        for (String s : snippets) {
            if (i++ >= 6) {
                break;
            }
            sb.append("- ").append(s.trim()).append('\n');
        }
        return sb.toString().trim();
    }

    private static String buildQuery(String... terms) {
        List<String> parts = new ArrayList<>();
        if (terms != null) {
            for (String t : terms) {
                if (t != null && !t.isBlank()) {
                    parts.add(t.trim());
                }
            }
        }
        return String.join(" ", parts);
    }

    private List<String> searchWikipedia(String query) throws Exception {
        String encoded = URLEncoder.encode(query, StandardCharsets.UTF_8);
        String searchUrl = "https://fr.wikipedia.org/w/api.php?action=opensearch&search="
                + encoded + "&limit=3&namespace=0&format=json";
        HttpRequest req = HttpRequest.newBuilder(URI.create(searchUrl))
                .timeout(Duration.ofSeconds(10))
                .header("User-Agent", "DeviceManager/1.0 (label-scan)")
                .GET()
                .build();
        HttpResponse<String> res = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() < 200 || res.statusCode() >= 300) {
            return List.of();
        }
        JsonNode root = objectMapper.readTree(res.body());
        if (!root.isArray() || root.size() < 4) {
            return List.of();
        }
        JsonNode titles = root.get(1);
        JsonNode descs = root.get(2);
        List<String> out = new ArrayList<>();
        for (int i = 0; i < titles.size(); i++) {
            String title = titles.get(i).asText("");
            String desc = descs.size() > i ? descs.get(i).asText("") : "";
            if (!title.isBlank()) {
                out.add(desc.isBlank() ? title : title + " — " + desc);
            }
        }
        return out;
    }

    private List<String> searchDuckDuckGo(String query) throws Exception {
        String encoded = URLEncoder.encode(query, StandardCharsets.UTF_8);
        String url = "https://api.duckduckgo.com/?q=" + encoded + "&format=json&no_html=1&skip_disambig=1";
        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(10))
                .header("User-Agent", "DeviceManager/1.0 (label-scan)")
                .GET()
                .build();
        HttpResponse<String> res = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() < 200 || res.statusCode() >= 300 || res.body() == null || res.body().isBlank()) {
            return List.of();
        }
        JsonNode root = objectMapper.readTree(res.body());
        List<String> out = new ArrayList<>();
        String abstractText = root.path("AbstractText").asText("");
        String heading = root.path("Heading").asText("");
        if (!abstractText.isBlank()) {
            out.add((heading.isBlank() ? "" : heading + " — ") + abstractText);
        }
        JsonNode related = root.path("RelatedTopics");
        if (related.isArray()) {
            for (JsonNode node : related) {
                if (out.size() >= 4) {
                    break;
                }
                String text = node.path("Text").asText("");
                if (!text.isBlank()) {
                    out.add(text);
                }
            }
        }
        return out;
    }
}
