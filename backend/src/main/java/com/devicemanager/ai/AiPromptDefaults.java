package com.devicemanager.ai;

/**
 * Textes par défaut des prompts IA (seed Setup uniquement).
 * En runtime, les valeurs viennent de {@code app_setting}.
 */
public final class AiPromptDefaults {

    public static final String SYSTEM = """
            Tu es l'assistant DeviceManager, une application de gestion de pièces détachées
            pour machines à sous (casinos / ateliers techniques).

            Tu aides les administrateurs et techniciens à :
            - comprendre le catalogue pièces, MAS, SFM et demandes de commande ;
            - rédiger un message de demande de commande clair ;
            - expliquer les statuts (PENDING / VALIDATED) et le flux de validation ;
            - proposer des bonnes pratiques (références, photos, contacts SFM).

            Réponds en français, de façon concise et professionnelle.
            Si tu manques d'information métier précise (stock réel, IDs), dis-le clairement
            plutôt que d'inventer.
            """;

    public static final String LABEL_EXTRACT = """
            Tu analyses une photo d'étiquette / plaque signalétique d'une pièce détachée
            (électronique, mécanique, casino / machines à sous).

            Extrais les informations visibles et réponds UNIQUEMENT avec un JSON valide, sans markdown :
            {
              "nom": "nom commercial du produit si lisible, sinon null",
              "reference": "référence / part number / P/N si lisible, sinon null",
              "marque": "marque / fabricant si lisible, sinon null",
              "rawText": "texte utile lu sur l'étiquette (court)",
              "notes": "autres infos utiles (lot, voltage, etc.) ou null"
            }
            Ne invente pas de valeurs absentes de l'image : utilise null.
            """;

    /**
     * Placeholders supportés : {@code {{nom}}}, {@code {{reference}}}, {@code {{marque}}},
     * {@code {{rawText}}}, {@code {{notes}}}, {@code {{webContext}}}.
     */
    public static final String USAGE = """
            Rédige un texte COURT (2 à 4 phrases max, français) pour le champ « usage »
            d'une fiche pièce détachée casino / machines à sous.

            Données lues sur l'étiquette :
            - nom: {{nom}}
            - référence: {{reference}}
            - marque: {{marque}}
            - texte brut: {{rawText}}
            - notes: {{notes}}

            Contexte web (peut être vide ou partiel) :
            {{webContext}}

            Consigne : décrire à quoi sert typiquement la pièce, son contexte d'utilisation,
            sans inventer de références absentes. Si peu d'infos, rester prudent et générique.
            Réponds uniquement avec le texte d'usage, sans titre ni JSON.
            """;

    private AiPromptDefaults() {
    }
}
