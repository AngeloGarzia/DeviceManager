# DeviceManager — Spécifications base de données & stockage (devis cloud)

Document destiné aux devis / RFQ auprès d’hébergeurs cloud.  
Stack applicative : **API Spring Boot 3.5 (Java 21)** + **frontend Angular (static)** + **MySQL 8.x**.

> Aucun secret ni mot de passe réel n’est inclus ici. Remplacer les placeholders lors du déploiement.

---

## 1. Vue d’ensemble des composants à héberger

| Composant | Techno | Rôle |
|-----------|--------|------|
| **API** | Conteneur Docker (JRE 21), port HTTP `8080` | Métier, auth JWT, uploads |
| **Frontend** | Site statique (HTML/JS) | SPA Angular |
| **Base de données** | **MySQL 8.0+** (cible validée : **8.4**) | Données métier + blobs optionnels |
| **Stockage fichiers** | **Soit** MySQL `LONGBLOB` + disque local **soit** **Cloudflare R2** (API S3-compatible) | Photos, PDF, devis, règles de jeux… |
| **SMTP** (optionnel) | Relais externe (Brevo, SES, etc.) | Mails commandes / alertes |
| **IA** (optionnel) | Clés API externes | Scan devis / PDF — hors infra DB |

Déploiement actuel de référence : API + static sur Render (free) + MySQL externe (ex. Aiven Free).

---

## 2. Base de données MySQL

### 2.1 Exigences techniques

| Critère | Valeur requise / recommandée |
|---------|------------------------------|
| Moteur | **MySQL 8.0 ou 8.4** (InnoDB) |
| Charset | `utf8mb4` (recommandé) |
| Timezone | UTC côté JDBC (`serverTimezone=UTC`) |
| SSL | **Requis en cloud** (`sslMode=REQUIRED` ou équivalent) |
| Accès | TCP distant depuis le serveur API (IP allowlist / VPC) |
| Migrations | **Flyway** (schéma versionné V1 → V28+) — `ddl-auto=validate` en prod |
| ORM | Hibernate / dialecte `MySQLDialect` |
| Pool connexions | HikariCP (timeout connexion 30 s) |

**Non supporté tel quel** : PostgreSQL, SQLite, MariaDB non validée (driver MySQL officiel).

### 2.2 Chaîne JDBC (exemples)

**Local / Docker :**
```text
jdbc:mysql://localhost:3306/device_manager?createDatabaseIfNotExist=true&useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC
```

**Cloud (ex. Aiven) :**
```text
jdbc:mysql://HOST:PORT/DATABASE?sslMode=REQUIRED&serverTimezone=UTC&allowPublicKeyRetrieval=true
```

Variables d’environnement API :
- `SPRING_DATASOURCE_URL`
- `SPRING_DATASOURCE_USERNAME`
- `SPRING_DATASOURCE_PASSWORD`
- `SPRING_JPA_HIBERNATE_DDL_AUTO=validate` (production)

### 2.3 Nom de base

- Local / Compose : `device_manager`
- Cloud : souvent `defaultdb` (Aiven) ou un schéma dédié — l’URL JDBC indique le nom.

### 2.4 Conteneur de référence (dev)

```yaml
# docker-compose.yml
image: mysql:8.4
MYSQL_DATABASE: device_manager
port: 3306
volume: mysql_data
```

Image Docker custom : `docker/mysql/Dockerfile` → `FROM mysql:8.4`.

### 2.5 Gestion du schéma

- **Flyway** activé, `baseline-on-migrate: true`, migrations dans `backend/src/main/resources/db/migration/`
- Versions actuelles : **V1 … V28** (évolutif)
- En production : **ne jamais** utiliser `hibernate.ddl-auto=update/create`

### 2.6 Principales tables métier

| Domaine | Tables (indicatif) |
|---------|-------------------|
| Organisation | `groupe`, `casino`, `atelier`, `coordonnees`, emails / téléphones |
| Utilisateurs | `users`, `refresh_token`, `atelier_responsable` |
| SFM / MAS | `sfm`, `sfm_contact`, `marque_mas`, `mas`, `deno`, `regle_jeux`, `mas_regle_jeux` |
| Pièces | `device`, `device_photo`, `device_document`, `stock_mouvement`, `device_prix_*` |
| Commandes | `commande`, `commande_ligne` (+ champs devis PDF) |
| Interventions | `intervention`, `intervention_ligne`, `interventions` (techniques) |
| FIT / maintenance | `fit`, `fit_ligne`, `visite_quadritrimestrelle`, `arret_maintenance` |
| Todo | `todo_tache` (dont signature / commentaire clôture en texte) |
| Config / logs | `app_setting`, `app_log` |
| **Stockage fichiers** | **`upload_blob`** (voir §3) |

### 2.7 Types « lourds » en base (hors object storage)

| Contenu | Stockage SQL | Remarque |
|---------|--------------|----------|
| Fichiers uploadés (mode local) | `upload_blob.data` **LONGBLOB** | Jusqu’à ~4 Gio théoriques / ligne MySQL |
| Signatures manuscrites (FIT, arrêts, Todo…) | **LONGTEXT** (data URL PNG) | Pas de fichier séparé |
| Logs applicatifs | `app_log` TEXT / MEDIUMTEXT | Rétention configurable (~5000 entrées) |

### 2.8 Dimensionnement MySQL (ordres de grandeur pour devis)

À adapter selon le volume réel (nombre d’ateliers / pièces / PDF) :

| Profil | Stockage utile estimé | RAM instance | vCPU | Notes |
|--------|----------------------|--------------|------|-------|
| **PoC / petit** | **1–5 Go** | 1 Go | 1 | Free tier acceptable si backups OK |
| **PME** | **20–50 Go** | 2–4 Go | 2 | Backups automatiques + SSL |
| **Avec blobs en MySQL** | **50–200 Go+** | 4 Go+ | 2–4 | Fortement recommandé de basculer vers S3 |
| **Avec S3 externe** | **5–20 Go** DB pure | 2–4 Go | 2 | DB allégée (métadonnées seulement) |

Paramètres à vérifier chez l’hébergeur :
- `max_allowed_packet` ≥ **32 Mo** (idéalement **64 Mo**) — uploads jusqu’à 20 Mo/fichier
- Connexions simultanées : au moins **20–50** pour l’API
- Backups quotidiens + rétention ≥ 7 jours
- Point-in-time recovery si possible

---

## 3. Stockage des images & documents

L’application a **deux modes** mutuellement exclusifs (bean Spring conditionnel).

### 3.1 Mode A — Local + MySQL (défaut actuel en prod free)

**Activation :** `APP_S3_ENABLED=false` (défaut)

**Comportement (`LocalStorageService`) :**
1. Écriture sur disque : dossier `APP_S3_LOCAL_FALLBACK_DIR` (prod Docker : `/app/uploads`)
2. **Copie durable** dans la table MySQL `upload_blob`
3. Servage HTTP : `GET /uploads/{filename}` (public, CORS GET)

**Schéma `upload_blob` :**
```sql
object_key   VARCHAR(512) PRIMARY KEY
data         LONGBLOB NOT NULL
content_type VARCHAR(100)
file_size    BIGINT
```

**Avantage :** survit au **disque éphémère** des PaaS free (Render, etc.).  
**Inconvénient :** grossit fortement la base ; I/O et backups plus lourds.

**Important pour devis PaaS :**
- Sans volume persistant, le dossier `/app/uploads` est **perdu au redéploiement** — la vérité reste MySQL.
- Prévoir un **disque persistant** (volume) si on veut éviter de recharger depuis MySQL à chaque boot, ou passer en mode S3.

### 3.2 Mode B — Cloudflare R2 (recommandé en cloud)

**Activation :** `APP_S3_ENABLED=true`

L’API utilise le SDK S3 AWS **uniquement comme client compatible** ; le stockage cible est **Cloudflare R2**.

**Variables :**
| Variable | Description |
|----------|-------------|
| `APP_S3_BUCKET` | Nom du bucket R2 |
| `APP_S3_REGION` | Toujours `auto` pour R2 |
| `AWS_ACCESS_KEY_ID` | Access Key ID du token R2 |
| `AWS_SECRET_ACCESS_KEY` | Secret Access Key du token R2 |
| `AWS_S3_ENDPOINT` | `https://<ACCOUNT_ID>.r2.cloudflarestorage.com` (API, pas public) |
| `APP_S3_PUBLIC_BASE_URL` | Custom domain prod uniquement (ex. `https://media.votredomaine.com`) — **pas** `*.r2.dev` |

**Comportement (`S3StorageService`) :**
- Préfixe objets : `spare-parts/{uuid}-{filename}`
- Client path-style + checksums « when required » (compat R2 / SDK 2.30+)
- URLs navigateur = `APP_S3_PUBLIC_BASE_URL` + clé (pas l'endpoint API)

**Setup Cloudflare prod :**
1. R2 → Create bucket
2. **URL de développement publique = OFF**
3. **Domaines personnalisés** → `media.votredomaine.com`
4. Token API R2 (Object Read & Write, scoped bucket)
5. Variables d'env API + redémarrage — détail : `docs/cloudflare-r2-setup.md`

**Dimensionnement object storage (indicatif) :**

| Contenu | Estimation |
|---------|------------|
| Photos pièces (optimisées max **900 px**, JPEG) | ~50–300 Ko / photo |
| PDF devis / notices / règles de jeux | 0,5–15 Mo / fichier (plafond app **20 Mo**) |
| Petite flotte | **5–20 Go** |
| Flotte multi-casino | **50–200 Go** + egress |

R2 : egress vers Internet souvent **0 $** (avantage devis vs AWS S3).

### 3.3 Types de fichiers gérés par l’appli

| Type | Formats | Limite applicative | Optimisation |
|------|---------|-------------------|--------------|
| Photos pièces détachées | JPEG, PNG, WebP, GIF… | Multipart **20 Mo** / fichier | Redim. max **900 px** + JPEG |
| Documents pièce | PDF (+ images selon flux) | 20 Mo | — |
| Devis commande | PDF / image | 20 Mo | — |
| Bon destruction MAS | PDF / image | 20 Mo | — |
| Règles de jeux | PDF | 20 Mo | — |
| Requête HTTP totale | — | **45 Mo** (`max-request-size`) | — |

Métadonnées (URL / clé / taille / type MIME) stockées en tables métier (`device_photo`, `device_document`, champs devis MAS/commande, `regle_jeux`, etc.).

### 3.4 Signatures (pas d’object storage)

Signatures manuscrites (FIT, arrêts maintenance, clôture Todo) : **data URL PNG en LONGTEXT** dans MySQL.  
Impact stockage DB faible à moyen selon volume de signatures.

---

## 4. Ressources serveur API (hors DB)

Référence Render free / Dockerfile :

| Paramètre | Valeur actuelle |
|-----------|-----------------|
| Runtime | Java 21 JRE |
| Heap | `-Xms128m -Xmx240m` |
| GC | SerialGC |
| Metaspace | max 128 Mo |
| Code cache | 32 Mo |
| Mémoire instance mini | **≥ 512 Mo** (confort : **1 Go**) |
| Disque conteneur | Au moins uploads + logs ; idéalement volume ou S3 |
| Healthcheck | `/actuator/health/liveness` |

Pour un hébergeur « correct » (pas free sleep) : **1 vCPU / 1 Go RAM** API + MySQL managé séparé + bucket S3.

---

## 5. Frontend

- Build static Angular (Node 24 en CI)
- Aucune base de données
- Variable de build : URL publique de l’API (`API_URL`)
- HTTPS + CORS : origines exactes dans `APP_CORS_ALLOWED_ORIGINS`

---

## 6. Checklist RFQ hébergeur

À coller dans un devis / questionnaire fournisseur :

1. **MySQL 8.0+** managé, SSL, backups quotidiens, rétention ?
2. Stockage utile disponible / prix au Go (profil **avec** ou **sans** LONGBLOB) ?
3. `max_allowed_packet` ≥ 32 Mo ?
4. Accès réseau depuis le runtime API (public IP allowlist ou VPC privé) ?
5. **Cloudflare R2** (bucket + domaine public + token API) ?
6. Volume persistant pour le conteneur API (si mode local sans R2) ?
7. Conteneur Docker Java 21 ou PaaS équivalent (1 Go RAM) ?
8. Hébergement static HTTPS pour le frontend ?
9. SMTP sortant ou intégration tierce ?
10. Localisation données (RGPD / UE) ?

---

## 7. Architecture cible recommandée (cloud payant)

```text
[ Navigateur ]
      │
      ├─ Static CDN / hébergeur web  →  frontend Angular
      │
      └─ HTTPS  →  API Spring Boot (1–2 instances)
                      │
                      ├─ MySQL 8 managé (métadonnées)
                      ├─ Cloudflare R2 (photos + PDF)   ← APP_S3_ENABLED=true
                      └─ SMTP (Brevo / SES / …)
```

**Mode free / low-cost actuel :**
```text
API (disque éphémère) + MySQL externe
  └─ fichiers dans upload_blob (LONGBLOB)  ← APP_S3_ENABLED=false
```

---

## 8. Variables d’environnement liées DB & stockage

| Variable | Obligatoire | Description |
|----------|-------------|-------------|
| `SPRING_DATASOURCE_URL` | oui | JDBC MySQL |
| `SPRING_DATASOURCE_USERNAME` | oui | Utilisateur DB |
| `SPRING_DATASOURCE_PASSWORD` | oui | Mot de passe DB |
| `SPRING_JPA_HIBERNATE_DDL_AUTO` | prod : `validate` | Pas de DDL Hibernate |
| `APP_S3_ENABLED` | non (`false`) | `true` = Cloudflare R2 |
| `APP_S3_BUCKET` | si R2 | Nom bucket |
| `APP_S3_REGION` | si R2 | `auto` |
| `AWS_ACCESS_KEY_ID` | si R2 | Token R2 Access Key ID |
| `AWS_SECRET_ACCESS_KEY` | si R2 | Token R2 Secret |
| `AWS_S3_ENDPOINT` | si R2 | `https://&lt;ACCOUNT_ID&gt;.r2.cloudflarestorage.com` |
| `APP_S3_PUBLIC_BASE_URL` | si R2 | Domaine public / `*.r2.dev` |
| `APP_S3_LOCAL_FALLBACK_DIR` | non | Dossier local si R2 off |

Références code / config :
- `backend/src/main/resources/application.yml`
- `backend/.env.production.example`
- `render.yaml`
- `docker-compose.yml`
- `LocalStorageService` / `S3StorageService` / `S3Config`

---

## 9. Synthèse « une phrase » pour le commercial hébergeur

> Application métier Java : besoin d’un **MySQL 8 managé (SSL, backups)** et, pour les fichiers, de **Cloudflare R2** (ou blobs MySQL en mode low-cost) ; plus un **runtime Docker Java 21 (~1 Go RAM)** et un **hébergement static HTTPS** pour le front.

---

*Document généré pour DeviceManager — à mettre à jour si les migrations Flyway ou la stratégie de stockage évoluent.*
