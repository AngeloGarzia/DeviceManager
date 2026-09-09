package com.devicemanager.security;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSBase;
import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.cos.COSObject;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentCatalog;
import org.apache.pdfbox.pdmodel.PDDocumentNameDictionary;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.interactive.action.PDAction;
import org.apache.pdfbox.pdmodel.interactive.action.PDActionJavaScript;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotation;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationLink;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationWidget;
import org.apache.pdfbox.pdmodel.common.PDDestinationOrAction;
import org.apache.tika.Tika;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Deuxième passe de validation upload : détection MIME Apache Tika + scan PDFBox
 * des objets {@code /JavaScript} / {@code /JS}. À appeler après
 * {@link FileMagicBytesValidator} (fail-fast).
 */
public final class DeepFileContentValidator {

    private static final Tika TIKA = new Tika();

    private static final Set<String> IMAGE_MIME = Set.of(
            "image/jpeg",
            "image/png",
            "image/gif",
            "image/webp"
    );

    private DeepFileContentValidator() {
    }

    /**
     * Valide une image : Tika doit détecter un MIME image supporté, compatible
     * avec le type déclaré par le client lorsqu'il est fourni.
     *
     * @param data                contenu binaire (déjà passé par magic bytes)
     * @param declaredContentType type déclaré client (peut être null / vide)
     */
    public static void validateImage(byte[] data, String declaredContentType) {
        String detected = detectMime(data);
        if (!IMAGE_MIME.contains(detected)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Photo illisible ou format non pris en charge");
        }
        assertDeclaredMatches(declaredContentType, detected, IMAGE_MIME,
                "Photo illisible ou format non pris en charge");
    }

    /**
     * Valide un PDF : Tika doit détecter {@code application/pdf}, le type déclaré
     * doit correspondre, et aucun objet JavaScript ne doit être présent.
     *
     * @param data                contenu binaire (déjà passé par magic bytes)
     * @param declaredContentType type déclaré client (peut être null / vide)
     */
    public static void validatePdf(byte[] data, String declaredContentType) {
        String detected = detectMime(data);
        if (!"application/pdf".equals(detected)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Le fichier doit être un PDF valide");
        }
        assertDeclaredMatches(declaredContentType, detected, Set.of("application/pdf"),
                "Le fichier doit être un PDF valide");
        rejectEmbeddedJavaScript(data);
    }

    private static String detectMime(byte[] data) {
        if (data == null || data.length == 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Fichier vide ou illisible");
        }
        try {
            return TIKA.detect(data).toLowerCase(Locale.ROOT);
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Fichier illisible");
        }
    }

    private static void assertDeclaredMatches(
            String declaredContentType,
            String detected,
            Set<String> allowedDeclared,
            String message) {
        if (declaredContentType == null || declaredContentType.isBlank()) {
            return;
        }
        String declared = declaredContentType.toLowerCase(Locale.ROOT).trim();
        int semi = declared.indexOf(';');
        if (semi > 0) {
            declared = declared.substring(0, semi).trim();
        }
        if (!allowedDeclared.contains(declared)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
        }
        if (!declared.equals(detected)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
        }
    }

    private static void rejectEmbeddedJavaScript(byte[] data) {
        try (PDDocument document = Loader.loadPDF(data)) {
            PDDocumentCatalog catalog = document.getDocumentCatalog();
            PDDestinationOrAction openAction = catalog.getOpenAction();
            if (openAction instanceof PDActionJavaScript) {
                throw scriptRejected();
            }
            if (openAction instanceof PDAction action && isJavaScriptAction(action)) {
                throw scriptRejected();
            }
            PDDocumentNameDictionary names = catalog.getNames();
            if (names != null && names.getJavaScript() != null) {
                throw scriptRejected();
            }
            for (PDPage page : document.getPages()) {
                List<PDAnnotation> annotations = page.getAnnotations();
                if (annotations == null) {
                    continue;
                }
                for (PDAnnotation annotation : annotations) {
                    if (annotationHasJavaScript(annotation)) {
                        throw scriptRejected();
                    }
                }
            }
            IdentityHashMap<COSDictionary, Boolean> visited = new IdentityHashMap<>();
            if (cosHasJavaScript(catalog.getCOSObject(), visited, 0)) {
                throw scriptRejected();
            }
        } catch (ResponseStatusException ex) {
            throw ex;
        } catch (IOException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Le fichier doit être un PDF valide");
        }
    }

    private static boolean annotationHasJavaScript(PDAnnotation annotation) throws IOException {
        if (annotation instanceof PDAnnotationLink link) {
            return isJavaScriptAction(link.getAction());
        }
        if (annotation instanceof PDAnnotationWidget widget) {
            return isJavaScriptAction(widget.getAction());
        }
        COSDictionary dict = annotation.getCOSObject();
        return dict.containsKey(COSName.JS) || dict.containsKey(COSName.JAVA_SCRIPT);
    }

    private static boolean isJavaScriptAction(PDAction action) {
        return action instanceof PDActionJavaScript;
    }

    private static boolean cosHasJavaScript(COSBase base, IdentityHashMap<COSDictionary, Boolean> visited, int depth) {
        if (base == null || depth > 32) {
            return false;
        }
        if (base instanceof COSObject cosObject) {
            return cosHasJavaScript(cosObject.getObject(), visited, depth + 1);
        }
        if (!(base instanceof COSDictionary dict)) {
            return false;
        }
        if (visited.put(dict, Boolean.TRUE) != null) {
            return false;
        }
        if (dict.containsKey(COSName.JS) || dict.containsKey(COSName.JAVA_SCRIPT)) {
            return true;
        }
        for (COSBase value : dict.getValues()) {
            if (cosHasJavaScript(value, visited, depth + 1)) {
                return true;
            }
        }
        return false;
    }

    private static ResponseStatusException scriptRejected() {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "PDF refusé : contenu scripté non autorisé");
    }
}
