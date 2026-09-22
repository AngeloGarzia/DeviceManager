# Déploiement Clever Cloud (auto-deploy GitHub)

| App | URL |
|-----|-----|
| **Front** (UI) | `https://devicemanager.cleverapps.io` |
| **Back** (API) | `https://api-devicemanager.cleverapps.io` |

## Mode choisi

**Push sur `main` → Clever déploie tout seul** (intégration GitHub).

- **Back** : build Maven sur Clever (`spring-boot:run`).
- **Front** : **pas de `ng build` sur Clever** (deadlock / hang esbuild récurrents).  
  GitHub Actions build Angular → commit `frontend/www` → Clever sert uniquement ces fichiers statiques.

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
| `CC_WEBROOT` | `frontend/www` |
| `CC_BUILD_COMMAND` | `echo "prebuilt frontend/www — skip ng build"` |
| `CC_STATIC_FLAGS` | `--page-fallback index.html` |

**Supprimer** si présentes : `APP_FOLDER`, `CC_PRE_RUN_HOOK`, `API_URL` (build), `NODE_OPTIONS`, `CC_OVERRIDE_BUILDCACHE`, tout `npm ci` / `ng build`.

Le dossier versionné `frontend/www` est produit par le job CI `publish-frontend-www` (message `[skip ci]`).

## GitHub Actions

Workflow : [`.github/workflows/ci-cd.yml`](./.github/workflows/ci-cd.yml)

| Événement | Action |
|-----------|--------|
| PR / push `main` | Tests backend + frontend + Trivy |
| Push `main` | Build Angular → met à jour `frontend/www` → Clever redéploie le static |
| Push `main` | Smoke `GET /actuator/health` |

### Variable GitHub (recommandé)

Settings → Secrets and variables → Actions → **Variables** :

| Name | Value |
|------|--------|
| `API_URL` | `https://api-devicemanager.cleverapps.io` |

## Smoke manuel

`GET https://api-devicemanager.cleverapps.io/actuator/health` → `{"status":"UP"}`  
UI : `https://devicemanager.cleverapps.io`
