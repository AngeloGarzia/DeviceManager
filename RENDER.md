# Déploiement production sur Render

## Architecture

| Service Render | Rôle |
|----------------|------|
| `device-manager-mysql` | MySQL 8.4 (private service + disque) |
| `device-manager-api` | API Spring Boot (Docker) |
| `device-manager-web` | Front Angular (static CDN) |

Fichier Blueprint : [`render.yaml`](./render.yaml)

## Prérequis

1. Compte [Render](https://dashboard.render.com)
2. Dépôt GitHub connecté (`AngeloGarzia/DeviceManager`)
3. Plan **Starter** (ou supérieur) — MySQL privé + disque ne sont pas sur le free tier

## Déploiement Blueprint (recommandé)

1. Dashboard Render → **New** → **Blueprint**
2. Sélectionner le dépôt `DeviceManager`, branche `main`
3. Valider `render.yaml`
4. Après le premier déploiement de l’API et du front :
   - Copier l’URL du front (`https://device-manager-web.onrender.com`)
   - Dans **device-manager-api** → Environment → renseigner :
     - `APP_CORS_ALLOWED_ORIGINS` = URL du front (sans slash final)
5. Redéployer l’API si besoin pour appliquer le CORS

Les variables `MYSQL_PASSWORD`, `MYSQL_ROOT_PASSWORD` et `APP_JWT_SECRET` sont générées automatiquement.

## URLs typiques

- Front : `https://device-manager-web.onrender.com`
- API : `https://device-manager-api.onrender.com`
- Health : `https://device-manager-api.onrender.com/actuator/health`

Comptes seed (à changer en prod) : `admin` / `admin123` · `tech` / `tech123`

## Photos

Par défaut les uploads partent sur le **disque Render** monté en `/var/data/uploads`.

Pour la production durable, activer S3 dans l’API :

```
APP_S3_ENABLED=true
APP_S3_BUCKET=...
APP_S3_REGION=...
AWS_ACCESS_KEY_ID=...
AWS_SECRET_ACCESS_KEY=...
```

## Mail

`APP_MAIL_ENABLED=false` par défaut (logs simulés).  
Pour activer SMTP, renseigner `MAIL_*` et `APP_MAIL_ENABLED=true`.

## Build local des images

```powershell
# API
docker build -t device-manager-api ./backend

# MySQL (contexte racine)
docker build -f docker/mysql/Dockerfile -t device-manager-mysql .
```

## Vérifications post-déploiement

1. `GET /actuator/health` → `{"status":"UP"}`
2. Login front avec `admin` / `admin123`
3. Créer une pièce (photo) et vérifier l’affichage
4. Contrôler CORS (pas d’erreur navigateur sur `/api/...`)

## Dépannage

| Symptôme | Action |
|----------|--------|
| API ne démarre pas | Logs : attendre MySQL ready ; vérifier `SPRING_DATASOURCE_*` |
| CORS bloqué | `APP_CORS_ALLOWED_ORIGINS` = URL exacte du front |
| Front appelle localhost | Rebuild front : `API_URL` doit pointer vers l’host API Render |
| Photos perdues au redeploy | Activer S3 (disque local OK tant que le Disk Render est attaché) |
