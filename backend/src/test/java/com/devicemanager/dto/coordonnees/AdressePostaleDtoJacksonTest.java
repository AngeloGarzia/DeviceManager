package com.devicemanager.dto.coordonnees;

import com.devicemanager.dto.AtelierRequest;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regresses the Clever prod 500 on PUT /api/ateliers/{id}:
 * {@code @Builder} without a no-args ctor → InvalidDefinitionException on adresse.
 */
class AdressePostaleDtoJacksonTest {

    private final JsonMapper mapper = JsonMapper.builder().findAndAddModules().build();

    @Test
    void deserializesAdresseNestedInAtelierRequest() {
        String json = """
                {
                  "nom": "Atelier Centre",
                  "casinoId": 1,
                  "adresse": {
                    "ligne1": "1 rue du Test",
                    "ville": "Balaruc",
                    "pays": "France"
                  },
                  "utilise": true
                }
                """;

        AtelierRequest req = mapper.readValue(json, AtelierRequest.class);

        assertThat(req.getNom()).isEqualTo("Atelier Centre");
        assertThat(req.getAdresse()).isNotNull();
        assertThat(req.getAdresse().getVille()).isEqualTo("Balaruc");
        assertThat(req.getAdresse().getLigne1()).isEqualTo("1 rue du Test");
    }
}
