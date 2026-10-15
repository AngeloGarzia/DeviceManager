# DeviceManager — Pitch utilisateur complet

**Document destiné aux démonstrations et formations** : techniciens d’atelier et administrateurs (casino / multi-ateliers).  
Il décrit **toutes** les fonctionnalités et les cas d’usage métier de l’application.

---

## 1. En une phrase

**DeviceManager** est l’outil métier des ateliers machines à sous : stock de pièces détachées, commandes, parc MAS, SFM, interventions, FIT, tâches, règles de jeux (QR), et assistant IA — le tout **par atelier**, avec des droits distincts pour **technicien** et **administrateur**.

---

## 2. Qui fait quoi (rôles)

| Rôle | Usage typique |
|------|----------------|
| **Technicien** | Travail quotidien sur **un** atelier (préféré) : pièces, stock, commandes, MAS, FIT, Todo, IA. |
| **Administrateur** | Tout le périmètre technicien + **changement d’atelier**, validation / réception des commandes, devis IA, comptes techniciens, casinos / ateliers, import Excel MAS, tâches récurrentes. |
| **Super-administrateur** | Tout le périmètre admin + configuration avancée (messagerie, stockage, IA, RGPD, planifications, logs) et gestion des rôles élevés. |
| **Visiteur anonyme** | Page confidentialité ; consultation des **règles de jeux** via QR (sans login). |

**Règle d’or** : les données affichées sont toujours celles de l’**atelier courant** (sélecteur Casino → Atelier pour l’admin ; atelier figé pour le technicien).

---

## 3. Accès, sécurité et confidentialité

### 3.1 Connexion
- Identifiant + mot de passe.
- Affichage / masquage du mot de passe.
- Option « Se souvenir » (session persistante).
- Lien **Mot de passe oublié** → e-mail de réinitialisation.
- Lien vers la page **Confidentialité / RGPD**.

### 3.2 Mot de passe oublié / réinitialisation
- Demande d’e-mail (`/forgot-password`).
- Nouveau mot de passe via lien token (`/reset-password`).

### 3.3 Première connexion / obligations
- **Changement de mot de passe forcé** si exigé par le compte.
- **Acceptation des mentions RGPD** obligatoire avant d’entrer dans l’application.

### 3.4 Session
- Déconnexion manuelle.
- **Inactivité** : avertissement puis déconnexion automatique (possibilité de « Rester connecté »).
- Restauration de session après rechargement de page (si « Se souvenir » / cookie de refresh).

### 3.5 Pages publiques
- `/confidentialite` — mentions légales / RGPD (textes paramétrables).
- `/public/r/{token}` — règles de jeux d’une MAS (lecture seule, QR).
- Visionneuse PDF / image d’une règle (y compris mobile).

---

## 4. Coque de l’application (toujours visible une fois connecté)

### 4.1 En-tête
- Nom de l’application, utilisateur, **rôle**, groupe, ville d’atelier.
- **Sélecteur Casino → Atelier** (admin uniquement) : au changement, recalcul de la **mémoire synaptique** et ouverture d’un **rapport de situation** (IA si activée, sinon résumé local).
- Technicien : atelier affiché en lecture seule.
- Alerte clignotante **arrêts maintenance** (si MAS arrêtées).
- Bouton **Assistant IA**.
- Bouton **Paramètres** (Setup).
- Bouton **Comptes** (admin uniquement).
- Bouton **Déconnexion**.

### 4.2 Navigation principale
| Entrée | Contenu |
|--------|---------|
| **Accueil** | Vue d’accueil atelier (calendrier Todo / tâches simples), pas la liste pièces. |
| **Pièces** | Créer · Liste · Utiliser · Bons d’intervention · Éditer le stock. |
| **Commandes** | Nouvelle demande · Liste (+ badge en attente) · Timeline. |
| **MAS** | Nouvelle · Liste · Suivi · Arrêts maintenance · Règles de jeux · Visite quadri · Interventions techniques · Fiches FIT · Suivi FIT (modèle 34) · **SFM**. |
| **Todo** | Tâches (+ badge actives / en retard). |

### 4.3 Pied de page
- **Tutoriel** : relance le parcours guidé de découverte.
- Lien **Confidentialité / RGPD**.

### 4.4 Parcours guidé (tour)
- Démarrage automatique au premier login.
- Relançable depuis le pied de page ou Setup.
- Couvre : atelier, SFM, MAS, pièces, stock, commandes, opérations MAS, Todo, IA, admin.

---

## 5. Accueil atelier (`/devices`)

**Cas d’usage** : voir d’un coup d’œil l’activité de la semaine et les tâches urgentes.

- Calendrier **semaine** des tâches (dont récurrentes) avec lien vers Todo.
- Liste de **tâches simples** (urgence, date, tri).
- Action **Signaler** → création rapide d’une tâche Todo.

---

## 6. Pièces détachées

### 6.1 Liste des pièces (`/devices/pieces`)
**Cas d’usage** : inventaire atelier, retrouver une référence, traiter les obsolètes et les ruptures.

- Statistiques : total, obsolètes, stock à zéro (raccourci vers commande).
- Recherche / filtres (dont affichage des obsolètes).
- Actions : Voir · Éditer · Supprimer.
- Miniature photo dans la ligne ; liste scrollable.

### 6.2 Créer / modifier une pièce (`/devices/new`, `/devices/:id/edit`)
**Cas d’usage** : référencer une pièce neuve (avec ou sans IA), mettre à jour fiche et documents.

**Capture image**
- Caméra (appareil préféré configurable dans Setup).
- Acquisition, galerie, édition (zoom / crop), max. de photos.
- Suppression de photos existantes ou nouvelles.

**Assistance IA** (si activée)
- **Scanner étiquette** → préremplissage nom / référence / marque / usage.
- **Scanner facture** (création) → extraction + revue.
- **Analyser un PDF** document → infos techniques.
- Aide à la rédaction du champ **usage**.

**Documents**
- Types : Manuel, Datasheet, Notice (PDF ou image).
- Remplacement / association à la fiche.

**Champs métier**
- Nom, référence, marque (via MAS / SFM), usage, informations techniques.
- Date d’acquisition, stock, prix HT, flag **obsolète**.
- Rattachement **SFM** et **MAS** (marque héritée).
- Retour contextuel possible (ex. depuis une demande de commande / brouillon).

### 6.3 Fiche détail (`/devices/:id`)
- Toutes les infos, galerie, documents.
- Dernier prix HT + **historique des prix** (saisies / devis).
- Modifier / supprimer.

### 6.4 Éditer le stock (`/devices/stock`)
**Cas d’usage** : inventaire physique, export pour direction / contrôle.

- Vue inventaire avec regroupements : aucun / SFM / marque / SFM+marque.
- Édition inline des quantités.
- Export **Excel** et **PDF**.

### 6.5 Utiliser une pièce (`/devices/utiliser`)
**Cas d’usage** : sortir du stock une ou plusieurs pièces et produire un bon d’intervention.

- Lignes multiples (recherche pièce + quantité ≤ stock).
- Bon : date/heure, emplacement, MAS, motif, diagnostic, travaux, observations.
- Option **associer une fiche FIT** + double signature (admin + technicien) et signataires.
- Si stock insuffisant : proposition de **demande de commande**.

### 6.6 Bons d’intervention pièces (`/devices/interventions`)
**Cas d’usage** : archive des sorties de stock / interventions liées aux pièces.

- Liste des bons avec lignes de pièces et détail.

---

## 7. Commandes

### 7.1 Nouvelle demande (`/order-request`)
**Cas d’usage** : le technicien (ou l’admin) commande des pièces au SFM / circuit atelier.

- Sélection multi-pièces + quantités.
- Possibilité de créer une pièce « à la volée ».
- Message / commentaire.
- **Aperçu des e-mails** (destinataires ADMIN / SFM / WARNING) avant envoi.
- Prefill possible pour stock à zéro (`?zeroStock=1`).
- Création → demande **en attente** (PENDING / SENT).

### 7.2 Liste des commandes (`/order-requests`)
**Lecture** : technicien + admin (statuts En attente · Validée · Reçue).

**Actions admin uniquement**
- **Valider** (déclenche les e-mails).
- Éditer les quantités (commande validée).
- **Réceptionner** (met à jour le stock).
- Associer / remplacer un **devis** (PDF / image).
- **IA devis** : proposition de mise à jour noms / références + revue.
- **IA prix** : confirmation des prix HT, alertes d’écart important.
- Supprimer une demande.
- Aperçu mails SFM avant validation.

### 7.3 Timeline commandes / atelier (`/order-timeline`)
**Cas d’usage** : vision chronologique croisée.

- Pistes filtrables : commandes, bons pièces, interventions techniques, FIT, mouvements de stock.

### 7.4 Relances automatiques (si planifiées)
- Relance des commandes encore en attente → administrateurs concernés (préférences d’alerte + scheduler Setup).

---

## 8. Machines à sous (MAS)

### 8.1 Liste (`/mas`)
- Stats, recherche, cartes / tableau.
- Voir · Éditer.
- Accès **étiquettes QR**.
- Colonne / accès **Jeux** (lien règles).
- Mémorisation de la sélection / focus après Voir / Éditer.

### 8.2 Étiquettes QR (`/mas/etiquettes-qr`)
**Cas d’usage** : imprimer des étiquettes A4 pour coller sur les machines ; le QR ouvre les règles **sans login**.

- Planche A4, formats, offsets, contours, sélection MAS.
- Légende : numéro MAS + « Règle de jeux ».
- Export / impression PDF.

### 8.3 Créer / modifier (`/mas/new`, `/mas/:id/edit`)
- Numéro, taux, marque (+ création), socle, denoms (multi + création), type machine, n° série.
- Dates MES / cessation, destination machine usagée.
- Association multi **règles de jeux**.
- **Statuts** : Utilisée · En réserve · Vendue · Détruite (changement avec confirmation).

### 8.4 Détail (`/mas/:id`)
- Fiche complète + alerte si règle manquante.
- Liens Suivi / FIT / Modifier.
- **QR public** : afficher, copier le lien, régénérer le jeton.
- **Bon de destruction** (si statut Détruite) : upload PDF / image.

### 8.5 Suivi (`/mas/suivi`)
**Cas d’usage** : historique d’une MAS.

- Timeline : bons pièces, interventions techniques, FIT, todos liés.

### 8.6 Arrêts maintenance (`/mas/arrets-maintenance`)
**Cas d’usage** : déclarer qu’une machine est arrêtée pour maintenance, puis la reprendre.

- Déclaration : MAS, date/heure, motif, option registre à jour → **signature**.
- Liste des arrêts actifs + **reprise** (signature si registre à jour).
- Badge shell + e-mails planifiés (si scheduler activé).

### 8.7 Règles de jeux (`/mas/regles-jeux` + visionneuse)
**Cas d’usage** : centraliser les PDF / images de règles et les rattacher aux MAS.

- Catalogue (upload PDF / image).
- **Analyse IA** à l’ajout (libellé / description).
- Éditer, remplacer le document, rattacher des MAS, supprimer.
- Visionneuse interne (PDF rendu canvas, compatible mobile).
- Accès public via QR (voir §3.5).

### 8.8 Visite quadritrimestrielle (`/mas/visites-quadri`)
**Cas d’usage** : suivi réglementaire des visites par couple **SFM × marque**.

- Obligations : OK / WARN / OVERDUE.
- Enregistrer une visite, consulter l’historique.
- Badge menu + e-mails planifiés.

### 8.9 Interventions techniques (`/mas/interventions`, `/new`)
**Cas d’usage** : interventions multi-machines hors simple sortie de pièce.

- Liste.
- Création multi-MAS : date, emplacement, motif, diagnostic, travaux, observations.
- Liens optionnels : commande, bon d’intervention, todos.
- Signatures FIT optionnelles.

### 8.10 Fiches FIT
| Écran | Usage |
|-------|--------|
| Liste `/mas/fit` | Parcourir les fiches. |
| Nouvelle `/mas/fit/new` | Créer une ligne : MAS, bon optionnel, date, socle, emplacement, motif, **2 signatures** + signataires. |
| Détail `/mas/fit/:id` | Historique des lignes + ajout. |
| Feuille `/mas/fit/feuille` | **Suivi FIT modèle 34** (affichage réglementaire, impression). |

- Une FIT peut aussi être créée automatiquement lors de l’**utilisation d’une pièce** liée à une MAS.

---

## 9. SFM (Services / Fournisseurs / Marques)

**Cas d’usage** : référencer les SFM qui couvrent les marques, leurs contacts (dont techniciens SFM), pour pièces, visites quadri et e-mails de commande.

- Liste avec recherche : Voir · Éditer · Supprimer.
- Formulaire : nom, marques couvertes (+ création marque).
- Contacts multiples : nom, téléphone, e-mail, flag **Technicien SFM**.
- Affectation d’un technicien SFM existant (sans doublon).
- Création possible depuis le formulaire pièce (retour automatique).
- Fiche détail.

---

## 10. Todo (tâches atelier)

**Cas d’usage** : organiser le travail quotidien, les urgences et les récurrences (contrôles périodiques).

### 10.1 Ouverture de la page
- Onglet **Tâches en retard** ouvert par défaut.
- Focus sur l’onglet puis sur les tuiles en retard.
- Clignotement des tuiles **plus rapide selon l’urgence** (jours de retard + sévérité).

### 10.2 Modèles & récurrences
- **Modèles de tâches simples** : toucher un modèle = créer la tâche immédiatement ; liste éditable par tous.
- Gestion des modèles (créer / éditer / supprimer / position).
- **Tâches récurrentes** (**admin**) : WEEKLY / MONTHLY / INTERVAL_DAYS, sévérité, MAS, horaires, dates début/fin, activer / pause, supprimer.
- Matérialisation automatique des occurrences (scheduler Setup).

### 10.3 Cycle de vie d’une tâche (occurrence)
| Bouton | Effet |
|--------|--------|
| **Démarrer** | `OPEN` → `IN_PROGRESS` (prise en charge). |
| **Remettre ouverte** | `IN_PROGRESS` → `OPEN`. |
| **Terminer** | Formulaire de **clôture** (nom + signature obligatoires, commentaire optionnel) → `DONE`. Si récurrence : génération éventuelle de la **prochaine occurrence**. |
| **Annuler** | → `CANCELLED`. |
| **Intervention** | Rattachement à une intervention technique et/ou un bon d’intervention. |

### 10.4 Nettoyage MAS (multi-machines)
- Type de tâche **Nettoyage MAS** : sélection de plusieurs machines de l’atelier, checklist cochable pendant le travail.
- Clôture avec **une seule signature** (comme une tâche standard) ; au moins une MAS cochée ; clôture partielle autorisée (seules les MAS cochées entrent dans l’historique).
- Historique consultable sur la **fiche MAS** et dans la **timeline** (événements Nettoyage).
- Page **Menu MAS → Nettoyage MAS** : grille colorée (vert = récent, rouge écarlate = ancien / jamais) + analyse (% ≤ 7 / 30 / 90 j, jamais, moyenne, répartition).

### 10.5 Autres fonctions Todo
- Création libre : titre, description, sévérité (HIGH / MEDIUM / LOW), MAS, **photos** (caméra / galerie).
- Filtres : Actives · En retard · Ouvertes · En cours · Clôturées · Annulées · Nettoyage MAS · Toutes + recherche + filtre jour.
- Stats en tête de page.
- Badge navigation (actives / retard).
- Rappels e-mail des tâches en retard (si scheduler activé).

---

## 11. Assistant IA & mémoire synaptique

### 11.1 Page Assistant (`/ai`)
**Cas d’usage** : poser des questions sur la situation de l’atelier (stock, todos, arrêts, commandes…).

- Bloc **Mémoire synaptique** : vue d’ensemble, faits récents, date de mise à jour.
- Actions : Actualiser · **Recalculer** le snapshot.
- Chat multi-tours (l’historique de conversation est renvoyé au modèle).
- Si IA désactivée / sans clé : message explicite ; la page reste accessible.

### 11.2 Mémoire synaptique
- Snapshot **par atelier** : overview chiffrée + anneau de faits récents.
- Mise à jour à chaque événement métier (pièces, commandes, MAS, todos…).
- Injectée dans le prompt du chat pour des réponses contextualisées.
- Recalcul au **changement d’atelier** (admin) + rapport de situation.

### 11.3 IA embarquée ailleurs
- Scan étiquette / facture / PDF pièce.
- Analyse règles de jeux.
- Devis & prix commande (**admin**).
- Aide rédaction usage.
- Briefing de situation (admin).

**Configuration** : Setup (super-admin) — fournisseur, modèle, prompts ; clés API via variables d’environnement serveur.

---

## 12. Paramètres — Setup (`/setup`)

### 12.1 Accessible à tous (technicien inclus)
- Relancer le **tutoriel**.
- Préférence **caméra** (choix de l’appareil).

### 12.2 Administrateur
- **Intégration** : import Excel des MAS.
- **Ateliers par casino** : CRUD ateliers (adresse, e-mails, téléphones, réseaux sociaux, responsables, utilisateurs préférés, actif / inactif).
- **Gérer les casinos** : CRUD.

### 12.3 Super-administrateur — catégories de réglages

**Messagerie**
- Activer l’envoi, expéditeur, e-mail admin, SMTP (hôte, port, identifiant, mot de passe).
- Test d’envoi.

**Sécurité**
- Durée d’expiration JWT.
- Origines CORS autorisées.

**Stockage**
- S3 / R2 (activation, bucket, région) ou dossier local d’uploads.

**Intelligence artificielle**
- Activer l’assistant.
- Fournisseur (OpenAI, Gemini, Groq, Mistral, OpenRouter, DeepSeek, Together, Fireworks…).
- Modèle (liste en ligne + filtre / actualisation).
- Prompts : système, extraction étiquette, facture, usage.

**RGPD / Mentions légales**
- Éditeur, directeur de publication, contact, hébergeur, responsable de traitement, sous-traitants, transferts, e-mail d’exercice des droits, adresse postale, date de mise à jour.
- Affichés sur la page publique `/confidentialite`.

**Rappels planifiés**
- Interrupteur global + fuseau horaire.
- Visites quadri (heure, jours d’alerte).
- Relance commandes (heure, jours).
- Arrêts maintenance (heure, jours).
- Matérialisation todos récurrents (heure).
- Todos en retard (heure).

**Logs applicatifs**
- Consultation / filtre / purge.

---

## 13. Comptes utilisateurs (admin)

**Écrans** : `/users`, `/users/new`, `/users/:id/edit`.

- Liste : identité, e-mail, username, rôle, atelier, dates, actions.
- Création / édition : prénom, nom, e-mail, username, mot de passe, rôle.
- Technicien → **atelier préféré** obligatoire.
- Admins : option **Recevoir les mails d’alerte**.
- **Super-admin** peut attribuer SUPER_ADMIN / ADMIN / TECHNICIEN.
- **Admin** classique : création de techniciens uniquement (pas d’élévation).
- Envoi possible d’e-mail de bienvenue.
- Garde-fou : au moins un administrateur par groupe.

---

## 14. Cas d’usage — scénarios de pitch

### Pour le technicien
1. Se connecter → accepter RGPD si demandé → atelier déjà positionné.
2. Accueil : voir les tâches de la semaine → traiter un retard Todo (Démarrer → Terminer + signature).
3. Créer une pièce : photo + scan étiquette IA → rattacher SFM / MAS → enregistrer.
4. Utiliser une pièce sur une MAS → bon d’intervention → option FIT signée.
5. Stock bas → nouvelle demande de commande → aperçu mails → envoi.
6. Consulter une MAS → ouvrir les règles / imprimer une étiquette QR pour le sol.
7. Poser une question à l’**assistant IA** sur les ruptures ou les todos.

### Pour l’administrateur
1. Changer d’atelier → lire le **rapport de situation**.
2. Valider / réceptionner les commandes ; joindre un devis ; laisser l’IA proposer prix / références.
3. Gérer casinos, ateliers, comptes techniciens.
4. Configurer les **tâches récurrentes** (contrôles périodiques).
5. Suivre visites quadri et arrêts maintenance (badges + mails).
6. (Super-admin) Activer l’IA, la messagerie, les rappels planifiés, les textes RGPD.

### Pour le terrain (sans compte)
1. Scanner le QR collé sur la MAS.
2. Consulter / faire défiler les règles de jeux (PDF ou image) sur téléphone.

---

## 15. Tableau récapitulatif des droits

| Capacité | Tech | Admin | Super-admin | Anonyme |
|----------|:----:|:-----:|:-----------:|:------:|
| Pièces, stock, utiliser, bons | ✓ | ✓ | ✓ | |
| Commandes (créer, consulter) | ✓ | ✓ | ✓ | |
| Valider / réceptionner / devis IA / supprimer commandes | | ✓ | ✓ | |
| MAS, SFM, FIT, interventions, règles, QR labels | ✓ | ✓ | ✓ | |
| Todo modèles + cycle + photos + clôture signée | ✓ | ✓ | ✓ | |
| Todo récurrences | | ✓ | ✓ | |
| Changer d’atelier + briefing | | ✓ | ✓ | |
| Comptes utilisateurs | | ✓* | ✓ | |
| Casinos / ateliers / import Excel | | ✓ | ✓ | |
| Mail, stockage, config IA, RGPD textes, schedulers, logs | | | ✓ | |
| Assistant IA (si activé) | ✓ | ✓ | ✓ | |
| Login / reset MDP / page RGPD / QR règles | | | | ✓ |

\* Un admin non super ne peut pas créer ni éditer des comptes SUPER_ADMIN / ADMIN.

---

## 16. Rappels automatiques (e-mails) — synthèse

Lorsque les planifications Setup sont actives :

| Rappel | Contenu typique |
|--------|------------------|
| Visites quadri | SFM × marque proches / dépassées. |
| Commandes | Relance des demandes encore en attente. |
| Arrêts maintenance | MAS encore arrêtées au-delà du seuil. |
| Todos | Occurrences récurrentes à matérialiser ; tâches en retard. |
| Validation commande | E-mails ADMIN / SFM / WARNING selon configuration. |

Les destinataires admin respectent en général la préférence **« Recevoir les mails d’alerte »**.

---

## 17. Glossaire rapide

| Terme | Signification |
|-------|----------------|
| **MAS** | Machine à sous (unité du parc). |
| **SFM** | Structure / fournisseur / marque de rattachement (contacts, couverture marques). |
| **FIT** | Fiche d’intervention technique (traçabilité signée). |
| **Bon d’intervention** | Document de sortie / usage de pièces détachées. |
| **Règle de jeux** | Document PDF/image associé à une MAS ; consultable via QR. |
| **Mémoire synaptique** | Snapshot IA de la situation d’un atelier. |
| **Todo** | Tâche atelier (ponctuelle, modèle ou occurrence récurrente). |
| **Purge en chaîne** | Super Admin : supprimer une pièce en archivant les bons liés et en conservant l’historique stock. |

---

*Document généré pour DeviceManager — à utiliser tel quel en pitch, formation ou onboarding. Mettre à jour ce fichier à chaque nouvelle fonctionnalité majeure.*
