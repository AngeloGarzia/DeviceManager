package com.devicemanager.dto;

import lombok.Builder;
import lombok.Data;

/**
 * Jeton d'accès public + chemin relatif pour le QR code.
 */
@Data
@Builder
public class MasPublicAccessTokenResponse {
    private String token;
    /** Chemin SPA relatif, ex. {@code /public/r/abc…}. */
    private String publicPath;
}
