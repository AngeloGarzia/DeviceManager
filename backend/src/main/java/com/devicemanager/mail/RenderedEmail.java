package com.devicemanager.mail;

/**
 * E-mail rendu (sujet + versions texte et HTML).
 */
public record RenderedEmail(String subject, String text, String html) {
}
