package com.devicemanager.security;

/**
 * Types de documents PDF attachés à une pièce détachée.
 */
public final class DeviceDocumentTypes {

    public static final String MANUAL = "MANUAL";
    public static final String DATASHEET = "DATASHEET";
    public static final String NOTICE = "NOTICE";

    private DeviceDocumentTypes() {
    }

    public static boolean isValid(String type) {
        return MANUAL.equals(type) || DATASHEET.equals(type) || NOTICE.equals(type);
    }

    public static String normalize(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String t = raw.trim().toUpperCase();
        return isValid(t) ? t : null;
    }
}
