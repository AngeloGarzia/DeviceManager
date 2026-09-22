# Déploiement Clever Cloud (auto-deploy GitHub)

| App | URL |
|-----|-----|
| **Front** (UI) | `https://devicemanager.cleverapps.io` |
| **Back** (API) | `https://api-devicemanager.cleverapps.io` |

## Mode choisi

**Push sur `main` → Clever déploie tout seul** (intégration GitHub).  
GitHub Actions ne fait que **CI** (tests) + **smoke** health — pas de `clever deploy`.

## Activer l’auto-deploy (console Clever)

Pour **chaque** app (back + front) :

1. https://console.clever.cloud.com → ouvrir l’application
2. Menu **Information** / **Create a deployment** / lien **GitHub**
3. Connecter le dépôt `AngeloGarzia/DeviceManager` si ce n’est pas déjà fait
4. Branche : `main`
5. Activer le déploiement automatique sur push

### App back (Java + Maven) — domaine `api-devicemanager.cleverapps.io`

| Variable | Valeur |
|----------|--------|
| `APP_FOLDER` | `backend` |
| `CC_JAVA_VERSION` | `21` |
| `MAVEN_DEPLOY_GOAL` | `-DskipTests spring-boot:run` |
| `CC_HEALTH_CHECK_PATH` | `/actuator/health` |
| `APP_CORS_ALLOWED_ORIGINS` | `https://devicemanager.cleverapps.io` |
| `APP_FRONTEND_BASE_URL` | `https://devicemanager.cleverapps.io` |
| + | `SPRING_DATASOURCE_*`, `APP_JWT_SECRET`, R2, mail, IA… |

**Ne pas définir `CC_RUN_COMMAND`.**  
`APP_KEEPALIVE_ENABLED=false`

### App front (Static) — domaine `devicemanager.cleverapps.io`

| Variable | Valeur |
|----------|--------|
| `APP_FOLDER` | `frontend` |
| `CC_NODE_VERSION` | `22` |
| `API_URL` | `https://api-devicemanager.cleverapps.io` |
| `NODE_OPTIONS` | `--max-old-space-size=1536` |
| `CC_WEBROOT` | `frontend/dist/frontend/browser` |
| `CC_BUILD_COMMAND` | `npm ci --include=dev && node scripts/inject-api-url.mjs && npm run build && ls -la dist/frontend/browser` |
| `CC_PRE_RUN_HOOK` | *(optionnel si le dist disparaît entre build et run)* même commande build sans `npm ci` si déjà installé |
| `CC_OVERRIDE_BUILDCACHE` | `frontend/dist/frontend/browser` |

Le script `npm run build` est un `ng build --configuration=production` simple.  
**`overrides.esbuild`: `^0.28.2`** unifie la version d’esbuild dans tout l’arbre (évite les deadlocks Go quand plusieurs binaires coexistent). Détails : [frontend/README.md](./frontend/README.md#clever-cloud--ci--esbuild-deadlock).

`APP_FOLDER` place le shell dans `frontend/` (d’où `dist/...` dans les commandes).  
`CC_WEBROOT` est résolu depuis la **racine du repo** → préfixe `frontend/`.

Dans les logs, cherche `ls -la` + `index.html` **avant** `Starting the application`. Sans ça, le build n’a pas produit le `dist`.

`API_URL` doit être présente au moment du build.

## GitHub Actions

Workflow : [`.github/workflows/ci-cd.yml`](./.github/workflows/ci-cd.yml)

| Événement | Action |
|-----------|--------|
| PR / push `main` | Tests backend + frontend + Trivy |
| Push `main` | Smoke `GET /actuator/health` (retries longs, Clever rebuild en parallèle) |

### Variable GitHub (recommandé)

Settings → Secrets and variables → Actions → **Variables** :

| Name | Value |
|------|--------|
| `API_URL` | `https://api-devicemanager.cleverapps.io` |

**Pas besoin** de `CLEVER_TOKEN` / `CLEVER_SECRET` / `CLEVER_APP_ID_*` pour ce mode.

## Smoke manuel

`GET https://api-devicemanager.cleverapps.io/actuator/health` → `{"status":"UP"}`  
UI : `https://devicemanager.cleverapps.io`
