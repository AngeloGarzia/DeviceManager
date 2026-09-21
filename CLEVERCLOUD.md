# Déploiement Clever Cloud (auto-deploy GitHub)

API : `https://devicemanager.cleverapps.io`

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

### App back (Java + Maven)

| Variable | Valeur |
|----------|--------|
| `APP_FOLDER` | `backend` |
| `CC_JAVA_VERSION` | `21` |
| `MAVEN_DEPLOY_GOAL` | `-DskipTests package` |
| `CC_RUN_COMMAND` | `java $JAVA_OPTS -jar target/device-manager-1.1.0.jar` |
| `APP_CORS_ALLOWED_ORIGINS` | URL exacte du **front** (sans `/`) |
| `APP_FRONTEND_BASE_URL` | même URL front |
| + | `SPRING_DATASOURCE_*`, `APP_JWT_SECRET`, R2, mail, IA… |

`APP_KEEPALIVE_ENABLED=false`

### App front (static / Node)

| Réglage | Valeur |
|---------|--------|
| Dossier / `APP_FOLDER` | `frontend` |
| Build | `npm ci && API_URL=https://URL-DU-BACK.cleverapps.io node scripts/inject-api-url.mjs && npm run build -- --configuration=production` |
| Publish | `dist/frontend/browser` |
| Rewrite SPA | `/*` → `/index.html` |

## GitHub Actions

Workflow : [`.github/workflows/ci-cd.yml`](./.github/workflows/ci-cd.yml)

| Événement | Action |
|-----------|--------|
| PR / push `main` | Tests backend + frontend + Trivy |
| Push `main` | Smoke `GET /actuator/health` (retries longs, Clever rebuild en parallèle) |

### Variable GitHub (optionnel mais recommandé)

Settings → Secrets and variables → Actions → **Variables** :

| Name | Value |
|------|--------|
| `API_URL` | `https://devicemanager.cleverapps.io` |

**Pas besoin** de `CLEVER_TOKEN` / `CLEVER_SECRET` / `CLEVER_APP_ID_*` pour ce mode.  
Tu peux supprimer les anciens secrets `RENDER_DEPLOY_HOOK_*`.

## Smoke manuel

`GET https://devicemanager.cleverapps.io/actuator/health`
