# DeviceManager

Application **mobile-first** de gestion d’atelier casino : inventaire des **pièces détachées**, suivi des **machines à sous (MAS)**, demandes de commande auprès des **SFM**, **bons d’intervention**, **interventions techniques**, fiches **FIT**, **visites quadritrimestrielles** SFM × marque, historique de **prix** (devis / IA) et assistant **IA**.

Chaque utilisateur travaille dans le périmètre d’un **atelier** (multi-tenant : Groupe → Casino → Atelier).

Version applicative : **1.1.0** (alignée `package.json` racine / `backend/pom.xml`).

---

## Structure du dépôt

```
DeviceManager/
├── backend/           ← API Spring Boot (+ Dockerfile)
├── frontend/          ← Angular 19 (+ inject-api-url pour Render)
├── docker/mysql/      ← image MySQL pour Render
├── sql/init.sql
├── render.yaml        ← Blueprint Render
├── RENDER.md          ← guide déploiement production
├── CHANGELOG.md
└── docker-compose.yml
```

Migrations Flyway : `backend/src/main/resources/db/migration/` (**V1 → V22**).

---

## Stack

| Couche | Technologie |
|--------|-------------|
| Backend | Java 21, Spring Boot 3.5, JPA, Security JWT (+ refresh cookie HttpOnly), Flyway, SpringDoc OpenAPI, Spring AI |
| Frontend | Angular 19, Material, Tailwind, driver.js (tutoriel), ExcelJS / jsPDF, ngx-image-cropper |
| Qualité | Checkstyle, SpotBugs, JaCoCo (backend) ; ESLint / tests Karma (frontend) ; Playwright e2e |
| Base | MySQL (Docker local ; Aiven Free en prod documenté) |
| Fichiers | Disque `/uploads` et/ou S3 (paramétrable) |
| Déploiement | Docker Compose, Blueprint Render (`RENDER.md`) |
| CI | GitHub Actions (tests, Checkstyle, SpotBugs, JaCoCo, lint, build, CodeQL) |

---

## Rôles

| Rôle | Capacités |
|------|-----------|
| **ADMIN** | Accès métier complet sur les ateliers de son **groupe** ; sélecteur Casino → Atelier ; **Comptes** ; **Paramètres** (Setup) ; validation / réception / suppression des commandes ; association devis ; alertes prix |
| **TECHNICIEN** | Accès métier (pièces, MAS, SFM, bons, interventions techniques, FIT, visites quadri, commandes en création/consultation, timeline, IA si activée) ; atelier **figé** (préféré) ; pas d’admin ni de validation/réception commande |

---

## Multi-atelier

- Hiérarchie : **Groupe → Casino → Atelier**.
- Le front envoie l’en-tête **`X-Atelier-Id`** sur les appels authentifiés.
- Admin : peut changer d’atelier (même groupe) ; le préféré est mémorisé.
- Technicien : uniquement son atelier préféré (sélecteur en lecture seule).
- Toutes les données métier sont **scopées à l’atelier courant**.

---

## Concepts métier

### Bon d’intervention
Consommation de **pièces détachées** (décrémente le stock). Créé via **Utiliser une pièce détachée**. Numérotation : `BI-{atelier}-{année}-#####`. MAS optionnelle ; option **Associer à la FIT** (+ signatures).

### Intervention technique
Fiche **libre** sur une ou plusieurs MAS (**sans** consommation de stock). Une visite multi-MAS crée **une ligne par machine** (même `visite_groupe_id`). Liens optionnels : commande, bon, FIT.

### FIT (fiche inventaire / intervention technique)
Document type réglementaire lié à une MAS (1 FIT par atelier + MAS). Historique de **lignes** signées (admin + technicien). Feuille de suivi type modèle 34 (`/mas/fit/feuille`).

### Visite quadritrimestrielle
Chaque **SFM** doit couvrir chaque **marque** de ses compétences **tous les 4 mois**. Les visites sont stockées en base. À l’ouverture de session (et au changement d’atelier), un contrôle **déterministe** calcule les échéances : badge / libellé **WARNING** clignotant sur le menu **MAS** si une échéance tombe dans **≤ 7 jours** (ou en retard / jamais visitée). L’assistant IA reçoit un résumé de ce statut dans son contexte (pas d’appel LLM dédié à la vérification).

### Dénomination MAS
Valeur numérique du référentiel **ou** case **Multi déno** → affichage **MultiDéno** partout (liste, détail, recherche).

### Documents uploadés
Règle transversale : **PDF ou image** (bons de destruction MAS, devis de commande, documents pièce). L’analyse texte IA de devis reste **PDF-only**.

### Prix & devis
Après association d’un devis à une commande validée/reçue : revue IA des désignations / références, puis revue des **prix unitaires** (±30 % + incohérences IA optionnelles). Historique des prix sur la fiche pièce ; alertes prix (admin).

### Liens optionnels

| De | Vers | Comment |
|----|------|---------|
| Bon | FIT | Case « Associer FIT » + MAS + signatures |
| Intervention technique | FIT | Case « Associer à la FIT » + signatures |
| Intervention technique | Bon / Commande | Sélecteurs filtrés sur les MAS choisies |
| Après IT | Bon ou ligne FIT | Modale post-enregistrement |
| Commande | Devis | Upload PDF/image (admin) + analyse IA |

### Statut MAS

| Code | Libellé |
|------|---------|
| `UTILISEE` | Machine utilisée |
| `EN_RESERVE` | En réserve |
| `VENDUE` | Vendue |
| `DETRUITE` | Détruite (+ bon de destruction PDF/image) |

**Règle** : une MAS **ne se supprime pas** ; on change uniquement son statut.

### Statut commande

| Code | Libellé UI |
|------|------------|
| `PENDING` / `SENT` | En attente (badge clignotant) |
| `VALIDATED` | Validée |
| `RECEIVED` | Reçue |

Liste des commandes triée : en attente → validée → reçue (puis date).

---

## Parcours de l’application

### Connexion & session

| Route | Description |
|-------|-------------|
| `/login` | Connexion |
| `/change-password` | Changement forcé (comptes démo / flag) — min. 8 caractères |
| Shell | Nom · rôle · groupe · ville ; sélecteur atelier (admin) ; **Déconnexion** |
| Footer | **Tutoriel** (driver.js) · Confidentialité / RGPD |
| `/confidentialite` | Mentions RGPD |
| Header | **Assistant IA** (si activé) · Comptes / Paramètres (admin) |

Redirect par défaut après login : **Liste des pièces** (`/devices`).

---

### Menu Pièces

| Entrée | Route | Options / actions |
|--------|-------|-------------------|
| Liste des pièces | `/devices` | Recherche ; détail / éditer / supprimer ; tuile stock à zéro → demande |
| Créer une pièce | `/devices/new` | Photos (caméra / galerie / crop), stock, obsolète, SFM, MAS, **scan IA étiquette**, documents PDF/image (**manuel / datasheet / notice**), info technique |
| Éditer / détail | `/devices/:id/edit`, `/devices/:id` | Fiche complète + documents + **historique des prix** / dernier prix |
| Utiliser une pièce | `/devices/utiliser` | Lignes de consommation → **bon** ; motif / diagnostic / travaux ; MAS optionnelle ; **Associer FIT** + signatures |
| Bons d’intervention | `/devices/interventions` | Archive des bons |
| Éditer le stock | `/devices/stock` | Ajustement quantités ; regroupement SFM / marque ; **export Excel / PDF** |

---

### Menu Commandes

| Entrée | Route | Options / actions |
|--------|-------|-------------------|
| Nouvelle demande | `/order-request` | Création `PENDING` ; preview mails ; notif admin |
| Liste des commandes | `/order-requests` | Liste triée par statut + **badge** pending ; statut « En attente » **clignotant** ; **ADMIN** : valider → mails SFM, éditer quantités, **joindre devis**, réceptionner (+ stock), supprimer |
| Timeline | `/order-timeline` | Colonnes : **Commandes \| Bons \| Interventions techniques \| FIT \| Stock** (filtres) |

**Cycle commande** : `PENDING` → `VALIDATED` (admin) → `RECEIVED` (admin, stock +).

---

### Menu MAS

| Entrée | Route | Options / actions |
|--------|-------|-------------------|
| Liste des MAS | `/mas` | N°, socle, marque, déno / **MultiDéno**, taux, **statut** ; pas de suppression |
| Nouvelle MAS | `/mas/new` | Formulaire ; création marque / déno ; **Multi déno** ; statut exclusif ; identification (type, n° série, dates, destination, bon destruction si détruite) |
| Détail / édition | `/mas/:id`, `/mas/:id/edit` | Liens vers Suivi, FIT, édition ; bon de destruction si `DETRUITE` |
| Suivi | `/mas/suivi` | Timeline filtrée MAS : **Bons \| Interventions techniques \| FIT** |
| Visite quadritrimestrielle | `/mas/visites-quadri` | Obligations SFM × marque ; enregistrer une visite ; historique ; alerte ≤ 7 j |
| Interventions techniques | `/mas/interventions` | Liste |
| Nouvelle IT | `/mas/interventions/new` | Multi-MAS ; motif / travaux ; liens commande & bon **filtrés** ; associer FIT ; **modale** ensuite (bon consommation ou ligne FIT) |
| Fiches FIT | `/mas/fit` | Liste |
| Nouvelle / détail FIT | `/mas/fit/new`, `/mas/fit/:id` | Création depuis MAS ; ajout de lignes signées |
| Suivi FIT (modèle 34) | `/mas/fit/feuille` | Feuille de suivi |

Badge **WARNING** clignotant sur le bouton **MAS** si des visites quadri sont dues / en retard.

---

### Menu SFM

| Route | Options / actions |
|-------|-------------------|
| `/sfm`, `/sfm/new`, `/sfm/:id`, `/sfm/:id/edit` | CRUD fournisseurs ; marques couvertes ; contacts (e-mails commande, technicien SFM réutilisable) ; **modale de modification** par contact |

---

### Administration (ADMIN)

| Entrée | Route | Options / actions |
|--------|-------|-------------------|
| Comptes | `/users`, `/users/new`, `/users/:id/edit` | Création ADMIN / TECHNICIEN ; atelier préféré obligatoire pour un technicien |
| Paramètres | `/setup` | Casinos & ateliers ; messagerie ; JWT / CORS ; S3 ; IA ; RGPD ; test e-mail ; **logs** applicatifs |

---

### Assistant IA

| Route | Options |
|-------|---------|
| `/ai` | Chat métier (si `AI_ENABLED`) ; contexte atelier incluant le résumé des visites quadri |
| Formulaire pièce | Scan d’étiquette (vision) |
| Liste commandes | Analyse devis (désignations / références / prix) |

---

## Modèle de données (aperçu)

### Pièce (`device`)
nom, référence, usage, date d’acquisition, stock, obsolete, photos, documents (manuel / datasheet / notice), info technique, FK `sfm`, FK `mas`, marque, dernier prix unitaire (dénormalisé).

### Historique prix
`device_prix_observation`, `device_prix_alerte` — observations confirmées depuis devis ; alertes d’écart.

### SFM
nom, contacts (téléphone, e-mail, flags réception commande / technicien SFM), marques couvertes (`sfm_marque`).

### MAS
numéro, socle, marque, déno **ou** `multi_deno`, taux redistribution, identification machine, **statut**, bon de destruction (si détruite).

### Commande
message, statut, dates demande / validation / réception, lignes (pièce + quantité), devis (fichier + métadonnées).

### Bon d’intervention
numéro, date, technicien, motif, travaux, lignes de pièces consommées, MAS optionnelle, lien FIT optionnel.

### Intervention technique
date, MAS, motif, travaux ; liens optionnels FIT / commande / bon ; `visite_groupe_id` multi-MAS.

### FIT
en-tête machine + lignes (date, socle, emplacement, motif, signatures admin & technicien, bon lié optionnel).

### Visite quadritrimestrielle
`visite_quadritrimestrelle` : atelier, SFM, marque, `date_visite`, notes, auteur.

---

## Variables d’environnement

Toutes les configs sensibles sont dans les fichiers `.env` du **backend** uniquement.

| Environnement | Fichier | Activation |
|---------------|---------|------------|
| Développement | `backend/.env.development` | défaut |
| Production | `backend/.env.production` | `APP_ENV=production` |

Le frontend n’a accès à aucun secret (JWT, BDD, mail, S3, clés IA, etc.).

---

## Lancer en local

```powershell
# MySQL
docker compose up -d

# Backend (charge .env.development automatiquement)
cd backend
$env:Path = "c:\Users\dell\device-manager\.tools\apache-maven-3.9.9\bin;" + $env:Path
mvn spring-boot:run

# Frontend
cd frontend
npm start
```

Production locale :

```powershell
cd backend
$env:APP_ENV="production"
mvn spring-boot:run
```

**Qualité backend (local)** :

```powershell
cd backend
mvn -B test checkstyle:check spotbugs:check jacoco:report jacoco:check
```

**Production Render** : voir [`RENDER.md`](./RENDER.md) — Blueprint `render.yaml` + MySQL Aiven Free.

| | |
|--|--|
| App | http://localhost:4200 |
| API | http://localhost:8080 |
| Swagger | http://localhost:8080/swagger-ui.html |
| Comptes seed | `admin` / `admin123` · `tech` / `tech123` |

---

## API (préfixes)

| Préfixe | Rôle |
|---------|------|
| `/api/auth` | login, refresh, logout, change-password |
| `/api/devices` | CRUD pièces + `PATCH /{id}/stock` + historique prix |
| `/api/prix-alertes` | alertes prix (admin) |
| `/api/sfm` | CRUD SFM + techniciens réutilisables |
| `/api/mas` | CRUD MAS (+ marques / déno / multi-déno / bon destruction) |
| `/api/visites-quadri` | status, warning-count, historique, enregistrement visite |
| `/api/order-requests` | demandes (+ validate / receive / devis / mail-preview) |
| `/api/interventions` | bons d’intervention |
| `/api/interventions-techniques` | interventions techniques |
| `/api/fit` | FIT (from-mas, lignes, signataires, feuille) |
| `/api/timeline` | événements agrégés (filtres `types`, `masId`) |
| `/api/ateliers` | ateliers / casinos / preferred |
| `/api/users` | comptes (admin) |
| `/api/setup` | paramètres + test mail |
| `/api/logs` | logs mémoire (admin) |
| `/api/ai` | status, chat, label-scan, analyses devis / prix |
| `/api/privacy` | contenu RGPD (GET public) |
| `/uploads/**` | photos / fichiers |

En-tête métier : **`X-Atelier-Id`**. Health : `/actuator/health`.

Architecture backend : `Controller → Service → Repository → MySQL` (+ DTO).

---

## Schéma des parcours (vue d’ensemble)

```mermaid
flowchart TB
  Login[Login] --> Shell[Shell atelier]
  Shell --> Pieces[Pièces]
  Shell --> Cmd[Commandes]
  Shell --> Mas[MAS]
  Shell --> Sfm[SFM]
  Shell --> Admin[Admin]

  Pieces --> Use[Utiliser pièce]
  Use --> Bon[Bon d'intervention]
  Bon -.-> Fit[FIT]

  Cmd --> Demande[Demande PENDING]
  Demande --> Val[VALIDATION admin]
  Val --> Devis[Devis + IA prix]
  Val --> Rec[RÉCEPTION + stock]

  Mas --> IT[Intervention technique]
  IT -.-> Bon
  IT -.-> Fit
  IT -.-> Demande
  Mas --> Suivi[Suivi multi-colonnes]
  Mas --> Fit
  Mas --> VQ[Visites quadri SFM x marque]
  Sfm --> VQ

  Cmd --> Timeline[Timeline atelier]
  Shell -.->|warningCount| VQ
```
